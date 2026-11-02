package com.downshift.app.core.runtime

import com.downshift.app.core.build.Build
import com.downshift.app.core.dsp.EngineMixer
import com.downshift.app.core.engine.EngineSpec
import com.downshift.app.core.engine.Engines
import com.downshift.app.core.model.DriveInputs
import com.downshift.app.core.model.FuelKind
import com.downshift.app.core.pack.LoadedPack
import com.downshift.app.core.powertrain.Transmissions
import com.downshift.app.core.powertrain.VirtualPowertrain
import com.downshift.app.core.sound.InductionModel
import com.downshift.app.core.sound.PopScheduler
import com.downshift.app.core.sound.SoundDirector
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

    fun setBuild(build: Build, pack: LoadedPack) = pendingBuild.set(build to pack)

    fun setFuel(fuel: FuelKind) = pendingFuel.set(fuel)

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
        val events = powertrain.step(inputs, dt)
        val s = powertrain.state
        val ind = induction.step(s.rpm, spec.redlineRpm, s.load, dt)
        val popList = pops.step(s.overrun, director.rpmNorm(s.rpm), SoundDirector.popIntensity(build), dt)
        mixer.render(director.plan(s, events, ind, popList, build, fuel), out, frames)
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
        }
        pendingFuel.getAndSet(null)?.let { fuel = it }
        if (pendingStart.getAndSet(false)) powertrain.start()
        if (pendingStop.getAndSet(false)) powertrain.stop()
    }

    private companion object {
        const val POP_SEED = 0x5EED_0F_70L

        fun specFor(build: Build) = Engines.spec(build.engine).withRedlineOffset(build.redlineOffset)
    }
}
