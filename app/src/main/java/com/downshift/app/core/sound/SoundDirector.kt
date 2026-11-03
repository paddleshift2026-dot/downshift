package com.downshift.app.core.sound

import com.downshift.app.core.build.Build
import com.downshift.app.core.build.ExhaustPart
import com.downshift.app.core.build.TransmissionPart
import com.downshift.app.core.model.FuelKind
import com.downshift.app.core.powertrain.PowertrainEvent
import com.downshift.app.core.powertrain.PowertrainState
import com.downshift.app.core.synth.PulseDrive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns the powertrain state into a SoundPlan: two neighbouring on-load loops and two off-load loops,
 * crossfaded by load with equal power and pitched by rpm, plus the one-shots, pops and induction voices.
 * A procedural engine has no loops: the plan drives the real-time pulse voice instead, which also makes
 * the cranking, the limiter's stutter and the spin-down after a stop itself.
 */
class SoundDirector(var voicing: Voicing, var procedural: Boolean = false) {
    fun plan(state: PowertrainState, events: List<PowertrainEvent>, induction: InductionStep, pops: List<Pop>, build: Build, fuel: FuelKind): SoundPlan {
        val v = voicing
        val level = build.volume.coerceIn(0.0, 1.0) * fuelGain(fuel)
        if (procedural) return pulsePlan(state, events, induction, pops, build, level)
        val shots = ArrayList<OneShot>(1)
        if (PowertrainEvent.STARTUP in events) v.startup?.let { shots += OneShot(it, level) }
        if (PowertrainEvent.SHUTDOWN in events) v.shutdown?.let { shots += OneShot(it, level) }
        if (!state.running) {
            val master = if (PowertrainEvent.SHUTDOWN in events) level else 0.0
            return SoundPlan(emptyList(), shots, emptyList(), false, SynthVoices.SILENT, build.exhaust, master, BASE_LOUDNESS)
        }

        val loops = ArrayList<LoopVoice>(6)
        // The startup clip covers cranking and the flare; the loops join once it settles or the driver revs.
        if (!(state.starting && state.load < STARTING_REV_LOAD)) {
            val load = state.load.coerceIn(0.0, 1.0)
            addLadder(loops, v.onClips, v.onRpms, sin(load * PI / 2), state.rpm)
            addLadder(loops, v.offClips, v.offRpms, cos(load * PI / 2), state.rpm)
            if (state.atLimiter) v.limiter?.let { loops += LoopVoice(it, 1.0, LIMITER_GAIN) }
        }

        val s = induction.state
        val loudness = BASE_LOUDNESS + LOAD_LOUDNESS * state.load + RPM_LOUDNESS * rpmNorm(state.rpm)
        val synth = SynthVoices(s.whistleHz, s.whistleGain, s.whineHz, s.whineGain)
        return SoundPlan(loops, shots, shiftPops(pops, events, build), induction.blowOff, synth, build.exhaust, level, loudness)
    }

    private fun pulsePlan(state: PowertrainState, events: List<PowertrainEvent>, induction: InductionStep, pops: List<Pop>, build: Build, level: Double): SoundPlan {
        if (!state.running) {
            // Still turning after the key is off: heard as the firing stops and the crank slows.
            val turning = state.rpm > SPIN_RPM || PowertrainEvent.SHUTDOWN in events
            val drive = PulseDrive(state.rpm, 0.0, combustion = false, cranking = false)
            return SoundPlan(emptyList(), emptyList(), emptyList(), false, SynthVoices.SILENT, build.exhaust, if (turning) level else 0.0, 1.0, drive)
        }
        // The startup flare runs rich and hard until the rpm settles back to idle.
        val flare = if (state.starting && !state.cranking) {
            FLARE_LOAD * ((state.rpm - voicing.idleRpm) / (FLARE_SPAN * voicing.idleRpm)).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val drive = PulseDrive(state.rpm, maxOf(state.load, flare), combustion = !state.cranking && !state.fuelCut, cranking = state.cranking)
        val s = induction.state
        val synth = SynthVoices(s.whistleHz, s.whistleGain, s.whineHz, s.whineGain)
        return SoundPlan(emptyList(), emptyList(), shiftPops(pops, events, build), induction.blowOff, synth, build.exhaust, level, 1.0, drive)
    }

    private fun shiftPops(pops: List<Pop>, events: List<PowertrainEvent>, build: Build): List<Pop> {
        val all = ArrayList<Pop>(pops.size + 2)
        all.addAll(pops)
        if (build.transmission == TransmissionPart.DCT && PowertrainEvent.SHIFT_UP in events) {
            all += DCT_POP
            all += DCT_POP
        }
        if (build.transmission == TransmissionPart.MT_BLIP && PowertrainEvent.SHIFT_DOWN in events) all += BLIP_POP
        return all
    }

    /** 0 at idle, 1 at the top recorded band. */
    fun rpmNorm(rpm: Double): Double {
        val v = voicing
        val top = maxOf(v.onRpms.lastOrNull() ?: v.idleRpm, v.offRpms.lastOrNull() ?: v.idleRpm)
        return if (top <= v.idleRpm) 0.0 else ((rpm - v.idleRpm) / (top - v.idleRpm)).coerceIn(0.0, 1.0)
    }

    private fun addLadder(loops: MutableList<LoopVoice>, clips: List<String>, rpms: List<Double>, gain: Double, rpm: Double) {
        if (gain < MIN_GAIN) return
        val ladderRpms = listOf(voicing.idleRpm) + rpms
        val w = BandLayout.weights(ladderRpms, rpm)
        add(loops, ladderClip(clips, w.low), rpm / ladderRpms[w.low], gain * w.lowGain)
        if (w.high != w.low) add(loops, ladderClip(clips, w.high), rpm / ladderRpms[w.high], gain * w.highGain)
    }

    private fun ladderClip(clips: List<String>, index: Int) = if (index == 0) voicing.idleClip else clips[index - 1]

    /** The idle clip sits at the bottom of both ladders; when both pick it, their powers add. */
    private fun add(loops: MutableList<LoopVoice>, clip: String, rate: Double, gain: Double) {
        if (gain < MIN_GAIN) return
        val r = rate.coerceIn(MIN_RATE, MAX_RATE)
        val i = loops.indexOfFirst { it.clip == clip }
        if (i < 0) {
            loops += LoopVoice(clip, r, gain)
        } else {
            val g = loops[i].gain
            loops[i] = loops[i].copy(gain = sqrt(g * g + gain * gain))
        }
    }

    companion object {
        private const val MIN_GAIN = 1e-4
        private const val MIN_RATE = 0.5
        private const val MAX_RATE = 2.0
        private const val LIMITER_GAIN = 0.8
        private const val STARTING_REV_LOAD = 0.05
        private const val BASE_LOUDNESS = 0.6
        private const val LOAD_LOUDNESS = 0.25
        private const val RPM_LOUDNESS = 0.15
        private const val SPIN_RPM = 30.0
        private const val FLARE_LOAD = 0.5
        private const val FLARE_SPAN = 0.6
        private val DCT_POP = Pop(0.7, 8.0, 3500.0)
        private val BLIP_POP = Pop(0.5, 6.0, 3000.0)

        /** Combustion cars already make their own noise, so they get the quietest baseline; EVs the fullest. */
        fun fuelGain(fuel: FuelKind): Double = when (fuel) {
            FuelKind.EV -> 1.0
            FuelKind.HYBRID -> 0.85
            FuelKind.ICE -> 0.6
            FuelKind.UNKNOWN -> 0.85
        }

        fun popIntensity(build: Build): Double = build.pops.coerceIn(0.0, 1.0) * when (build.exhaust) {
            ExhaustPart.STOCK -> 0.15
            ExhaustPart.SPORT -> 0.5
            ExhaustPart.TRACK -> 1.0
        }
    }
}
