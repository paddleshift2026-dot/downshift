package com.downshift.app.core.runtime

import com.downshift.app.core.build.Build
import com.downshift.app.core.build.EngineId
import com.downshift.app.core.build.ExhaustPart
import com.downshift.app.core.dsp.EngineMixer
import com.downshift.app.core.dsp.GainGlide
import com.downshift.app.core.engine.EngineSpec
import com.downshift.app.core.engine.Engines
import com.downshift.app.core.input.SpeedSmoother
import com.downshift.app.core.model.DriveInputs
import com.downshift.app.core.model.FuelKind
import com.downshift.app.core.pack.LoadedPack
import com.downshift.app.core.powertrain.Transmissions
import com.downshift.app.core.powertrain.VirtualPowertrain
import com.downshift.app.core.sound.InductionModel
import com.downshift.app.core.sound.PopScheduler
import com.downshift.app.core.sound.SoundDirector
import com.downshift.app.core.synth.EngineVoice
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** What the screen shows about the engine; written once per audio block. gear: 1.. drive, 0 park/neutral, -1 reverse. */
data class Telemetry(val running: Boolean, val rpm: Double, val gear: Int, val load: Double, val boost: Double, val overrun: Boolean, val atLimiter: Boolean) {
    companion object {
        val OFF = Telemetry(false, 0.0, 0, 0.0, 0.0, false, false)
    }
}

/**
 * The whole engine for one audio stream. Each renderBlock steps the powertrain, induction and pops by the block's
 * duration, asks the director what to play and has the mixer render it, so control and sound move together.
 * sampleRate is the output rate (use the device's native rate for the low-latency path); packs at other rates are
 * resampled. setBuild, setFuel, start and stop may come from any thread and take effect at the start of the next block.
 */
class EngineRuntime(pack: LoadedPack, build: Build, fuel: FuelKind, val sampleRate: Int = pack.sampleRate) {
    @Volatile
    var telemetry: Telemetry = Telemetry.OFF
        private set

    /**
     * The engine has been off and silent for QUIET_SEC (spun down, no one-shot playing), so blocks are written as
     * zeros without running the mixer: idle synthesis cost about 2.5% of a core in the garage (M2 measurement).
     */
    @Volatile
    var quiet = false
        private set
    private var silentSec = 0.0

    @Volatile private var situationGain = 1.0
    private val situationGlide = GainGlide(1.0)

    private val pendingBuild = AtomicReference<Pair<Build, LoadedPack>?>(null)
    private val pendingFuel = AtomicReference<FuelKind?>(null)
    private val pendingStart = AtomicBoolean(false)
    private val pendingStop = AtomicBoolean(false)

    private var build = build
    private var pack = pack
    private var fuel = fuel
    private var spec: EngineSpec = specFor(build)
    private val powertrain = VirtualPowertrain(spec, Transmissions.of(build.transmission), build.transmission)
    private val induction = InductionModel(build.intake)
    private val pops = PopScheduler(POP_SEED)
    private val director = SoundDirector(pack.voicing)
    private val mixer = EngineMixer(sampleRate, pack.clips, clipRate = pack.sampleRate)
    private val speedSmoother = SpeedSmoother()
    private var voice: EngineVoice? = null
    private var voiceFor: Pair<EngineId, ExhaustPart>? = null

    init {
        syncVoice()
    }

    fun setBuild(build: Build, pack: LoadedPack) = pendingBuild.set(build to pack)

    fun setFuel(fuel: FuelKind) = pendingFuel.set(fuel)

    /** The situation's share of the volume (guidance, school zone, passenger, mute), 0..1; glides in over blocks. */
    fun setSituationGain(g: Double) {
        situationGain = g.coerceIn(0.0, 1.0)
    }

    fun start() {
        pendingStop.set(false)
        pendingStart.set(true)
    }

    fun stop() {
        pendingStart.set(false)
        pendingStop.set(true)
    }

    fun renderBlock(inputs: DriveInputs, out: FloatArray, frames: Int) {
        applyPending()
        val dt = frames.toDouble() / sampleRate
        val speed = speedSmoother.step(inputs.speedMps, dt)
        val events = powertrain.step(if (speed == inputs.speedMps) inputs else inputs.copy(speedMps = speed), dt)
        val s = powertrain.state
        val ind = induction.step(s.rpm, spec.redlineRpm, s.load, dt)
        val popList = pops.step(s.overrun, director.rpmNorm(s.rpm), SoundDirector.popIntensity(build), dt)
        val planned = director.plan(s, events, ind, popList, build, fuel)
        val plan = planned.copy(master = planned.master * situationGlide.next(situationGain, dt))
        val still = !s.running && s.rpm == 0.0 && events.isEmpty() && !mixer.shotsPlaying
        silentSec = if (still) silentSec + dt else 0.0
        quiet = silentSec >= QUIET_SEC
        if (quiet) java.util.Arrays.fill(out, 0, frames, 0f) else mixer.render(plan, out, frames)
        telemetry = Telemetry(s.running, s.rpm, s.gear, s.load, ind.state.boost, s.overrun, s.atLimiter)
    }

    private fun applyPending() {
        pendingBuild.getAndSet(null)?.let { (b, p) ->
            build = b
            spec = specFor(b)
            powertrain.spec = spec
            powertrain.gears = Transmissions.of(b.transmission)
            powertrain.transmission = b.transmission
            induction.part = b.intake
            if (p !== pack) {
                pack = p
                mixer.setClips(p.clips, p.sampleRate)
                director.voicing = p.voicing
            }
            syncVoice()
        }
        pendingFuel.getAndSet(null)?.let { fuel = it }
        if (pendingStart.getAndSet(false)) powertrain.start()
        if (pendingStop.getAndSet(false)) powertrain.stop()
    }

    /** A procedural pack plays through the pulse voice; a new engine or exhaust reconfigures it (crossfaded). */
    private fun syncVoice() {
        director.procedural = pack.procedural
        if (!pack.procedural) {
            voice = null
            voiceFor = null
        } else {
            val key = build.engine to build.exhaust
            val v = voice
            if (v == null) {
                voice = EngineVoice(sampleRate, spec, build.exhaust, VOICE_SEED)
            } else if (key != voiceFor) {
                v.configure(spec, build.exhaust)
            }
            voiceFor = key
        }
        mixer.setVoice(voice)
    }

    private companion object {
        const val POP_SEED = 0x5EED_0F_70L
        const val VOICE_SEED = 0x0E_4612L
        const val QUIET_SEC = 0.5 // pops, blow-off and the exhaust body ring out well within this

        fun specFor(build: Build) = Engines.spec(build.engine).withRedlineOffset(build.redlineOffset)
    }
}
