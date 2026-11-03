package com.downshift.app.core.synth

import com.downshift.app.core.build.EngineId
import com.downshift.app.core.build.ExhaustPart
import com.downshift.app.core.dsp.Biquad
import com.downshift.app.core.dsp.GainRamp
import com.downshift.app.core.dsp.NoiseSource
import com.downshift.app.core.dsp.SineOsc
import com.downshift.app.core.engine.EngineSpec
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/** What the voice is told each block. combustion=false: the engine turns without firing (cranking, fuel cut, spin-down). */
data class PulseDrive(val rpm: Double, val load: Double, val combustion: Boolean, val cranking: Boolean) {
    companion object {
        val OFF = PulseDrive(0.0, 0.0, combustion = false, cranking = false)
    }
}

/**
 * Real-time exhaust synthesis. The crank angle is integrated sample by sample and each cylinder fires a short pressure
 * pulse at its angle in the firing order, so the note is the firing rate and the exhaust resonances stay put at any rpm.
 * Pulses travel their bank's path (runner stagger, bank delay), ring the tailpipe (a feedback comb, inverted like an
 * open pipe) and pick up the body's resonances. Load sets loudness and brightness; small per-cycle and per-cylinder
 * variations keep it from sounding machine-made. With combustion off the cylinders only pump: quieter and duller.
 *
 * render() allocates nothing; configure() rebuilds the exhaust path and crossfades to it.
 */
class EngineVoice(sampleRate: Int, spec: EngineSpec, exhaust: ExhaustPart, seed: Long = 7) {
    private val fs = sampleRate.toDouble()
    private val random = Random(seed)
    private val noise = NoiseSource(seed xor 0x5DEECE66DL)

    /** Pulses fired so far (for tests and telemetry). */
    var fired = 0L
        private set

    // The firing plan, per event in cycle order.
    private var angles = DoubleArray(0)
    private var eventBank = IntArray(0)
    private var eventDelay = DoubleArray(0) // runner stagger + bank path + this cylinder's own offset, in samples
    private var eventGain = DoubleArray(0)
    private var timbre = Timbre.of(spec.id, exhaust)
    private var cylinderGain = 1.0

    // A resting crank sits between firings, so the first pulse comes after some rotation, not at the first sample.
    private var crank = REST_DEG
    private var next = 0
    private var lastRpm = 0.0
    private var lastLoad = 0.0
    private var cranked = 0 // samples since the starter was asked for

    // Sounding pulses are packed into [0, count) so the per-sample loop only visits those.
    private var count = 0
    private val started = BooleanArray(MAX_PULSES)
    private val wait = DoubleArray(MAX_PULSES)
    private val amp = DoubleArray(MAX_PULSES)
    private val rise = DoubleArray(MAX_PULSES)
    private val riseK = DoubleArray(MAX_PULSES)
    private val decay = DoubleArray(MAX_PULSES)
    private val decayK = DoubleArray(MAX_PULSES)
    private val turb = DoubleArray(MAX_PULSES)
    private val bankOf = IntArray(MAX_PULSES)
    private val bankIn = DoubleArray(2)

    private var pipe: ExhaustPath
    private var oldPipe: ExhaustPath? = null
    private var fadeLeft = 0
    private var turbLp = 0.0
    private var quiet = Int.MAX_VALUE

    private val starter = SineOsc(fs)
    private val starter2 = SineOsc(fs)
    private val starterGain = GainRamp(0f)

    init {
        pipe = plan(spec, exhaust)
    }

    /** New engine or exhaust: the firing plan changes at once, the exhaust path crossfades over FADE samples. */
    fun configure(spec: EngineSpec, exhaust: ExhaustPart) {
        oldPipe = pipe
        pipe = plan(spec, exhaust)
        fadeLeft = FADE
    }

    fun render(drive: PulseDrive, out: FloatArray, frames: Int) {
        val rpm0 = lastRpm
        val load0 = lastLoad
        val rpm1 = drive.rpm.coerceAtLeast(0.0)
        val load1 = drive.load.coerceIn(0.0, 1.0)
        // The starter motor engages a relay's delay after the key.
        cranked = if (drive.cranking) cranked + frames else 0
        starterGain.rampTo(if (drive.cranking && cranked > RELAY_MS * fs / 1000) STARTER_GAIN else 0f, frames)
        if (drive.cranking) {
            starter.freqHz = starterHz(rpm1)
            starter2.freqHz = 2 * starterHz(rpm1)
        }
        // Long silence: nothing is ringing any more, so skip the work (and stay exactly silent).
        if (rpm0 <= 0.0 && rpm1 <= 0.0 && !drive.cranking && starterGain.value == 0f && quiet > QUIET_SAMPLES) {
            java.util.Arrays.fill(out, 0, frames, 0f)
            return
        }
        val gain = OUT_GAIN * cylinderGain
        for (i in 0 until frames) {
            val f = (i + 1).toDouble() / frames
            val rpm = rpm0 + (rpm1 - rpm0) * f
            val load = load0 + (load1 - load0) * f
            turn(rpm, load, drive.combustion)
            bankIn[0] = 0.0
            bankIn[1] = 0.0
            turbLp += TURB_COEF * (noise.next() - turbLp)
            val n = turbLp * TURB_NORM
            val sounding = count > 0
            var p = count - 1
            while (p >= 0) {
                if (!started[p]) {
                    wait[p] -= 1.0
                    if (wait[p] > 0.0) {
                        p--
                        continue
                    }
                    val t0 = -wait[p]
                    rise[p] = riseK[p].pow(t0)
                    decay[p] = decayK[p].pow(t0)
                    started[p] = true
                }
                val v = amp[p] * (1 - rise[p]) * decay[p]
                rise[p] *= riseK[p]
                decay[p] *= decayK[p]
                bankIn[bankOf[p]] += v * (1 - turb[p] + turb[p] * n)
                if (decay[p] < PULSE_END) moveLastTo(p) // already-visited pulses fill the gap, so walking down stays correct
                p--
            }
            var y = pipe.process(bankIn)
            val old = oldPipe
            if (old != null) {
                val w = 1.0 - fadeLeft.toDouble() / FADE
                y = old.process(bankIn) * (1 - w) + y * w
                if (--fadeLeft <= 0) oldPipe = null
            }
            var s = (y * gain).toFloat()
            val sg = starterGain.next()
            if (sg > 0f) s += sg * (starter.next() + 0.45f * starter2.next())
            out[i] = s
            quiet = if (sounding || sg > 0f) 0 else minOf(quiet, Int.MAX_VALUE - 1) + 1
        }
        lastRpm = rpm1
        lastLoad = load1
    }

    /** Advances the crank by one sample and fires every cylinder whose angle it passed. */
    private fun turn(rpm: Double, load: Double, combustion: Boolean) {
        if (rpm <= 0.0 || angles.isEmpty()) return
        val step = 6.0 * rpm / fs // degrees per sample
        var left = step
        while (true) {
            var dist = angles[next] - crank
            if (dist < 0) dist += CYCLE_DEG
            if (dist >= left) {
                crank += left
                break
            }
            crank += dist
            left -= dist
            fire(next, rpm, load, combustion, sinceOnset = left / step)
            next = (next + 1) % angles.size
        }
        if (crank >= CYCLE_DEG) crank -= CYCLE_DEG
    }

    private fun fire(e: Int, rpm: Double, load: Double, combustion: Boolean, sinceOnset: Double) {
        val t = timbre
        val bright = if (combustion) load else 0.0
        var a = if (combustion) AMP_FLOOR + (1 - AMP_FLOOR) * load.pow(0.8) else MOTORING_AMP
        val sigma = if (combustion) JITTER_A_BASE + JITTER_A_IDLE * (1 - load) else JITTER_A_MOTORING
        a *= eventGain[e] * (1 + sigma * gauss())
        if (a <= 0.0) return
        val scale = (REFERENCE_RPM / rpm).pow(RPM_EXPONENT).coerceIn(MIN_SCALE, MAX_SCALE)
        var riseMs = t.riseMs * scale * (1.8 - 0.8 * bright)
        var decayMs = t.decayMs * scale * (1.15 - 0.3 * bright)
        if (!combustion) {
            riseMs *= 2.2
            decayMs *= 1.3
        }
        // Normalise so `a` is the pulse's peak: (1 - e^(-t/rise)) e^(-t/decay) peaks at rise·ln(1 + decay/rise).
        val tp = riseMs * ln(1 + decayMs / riseMs)
        val peak = (1 - exp(-tp / riseMs)) * exp(-tp / decayMs)
        val slot = freeSlot()
        started[slot] = false
        wait[slot] = BASE_LATENCY_MS * fs / 1000 + eventDelay[e] + JITTER_T_MS * fs / 1000 * gauss() - sinceOnset
        amp[slot] = a / peak
        riseK[slot] = exp(-1000.0 / (riseMs * fs))
        decayK[slot] = exp(-1000.0 / (decayMs * fs))
        turb[slot] = (t.turbulence * (0.4 + 0.6 * bright) * if (combustion) 1.0 else 1.4).coerceAtMost(0.9)
        bankOf[slot] = eventBank[e]
        fired++
    }

    private fun freeSlot(): Int {
        if (count < MAX_PULSES) return count++
        var quietest = 0
        for (p in 1 until MAX_PULSES) if (amp[p] * decay[p] < amp[quietest] * decay[quietest]) quietest = p
        return quietest
    }

    private fun moveLastTo(p: Int) {
        val last = --count
        if (p == last) return
        started[p] = started[last]
        wait[p] = wait[last]
        amp[p] = amp[last]
        rise[p] = rise[last]
        riseK[p] = riseK[last]
        decay[p] = decay[last]
        decayK[p] = decayK[last]
        turb[p] = turb[last]
        bankOf[p] = bankOf[last]
    }

    /** Sum of three uniforms: close enough to a unit gaussian, and cheap. */
    private fun gauss() = (random.nextDouble() + random.nextDouble() + random.nextDouble() - 1.5) * 2.0

    private fun plan(spec: EngineSpec, exhaust: ExhaustPart): ExhaustPath {
        val events = FiringPlans.of(spec)
        timbre = Timbre.of(spec.id, exhaust)
        cylinderGain = (4.0 / spec.cylinders).pow(0.35)
        angles = DoubleArray(events.size) { events[it].angleDeg }
        eventBank = IntArray(events.size) { events[it].bank }
        val seen = IntArray(2)
        eventDelay = DoubleArray(events.size) { i ->
            val b = events[i].bank
            val runner = timbre.staggerMs * seen[b]++
            val bankPath = if (b == 1) timbre.bankDelayMs else 0.0
            val own = DISP_T_MS * (2 * random.nextDouble() - 1)
            (runner + bankPath + own) * fs / 1000
        }
        eventGain = DoubleArray(events.size) { 1 + DISP_GAIN * (2 * random.nextDouble() - 1) }
        next = angles.indexOfFirst { it > crank }.let { if (it < 0) 0 else it }
        return ExhaustPath(fs, timbre, FiringPlans.banks(spec.id))
    }

    /** Per bank a tailpipe comb, then the shared body resonances. */
    private class ExhaustPath(fs: Double, t: Timbre, private val banks: Int) {
        private val buf = Array(banks) { FloatArray((t.tailMs[it] * fs / 1000).toInt().coerceAtLeast(1)) }
        private val idx = IntArray(banks)
        private val feedback = t.combFeedback
        private val bankGain = DoubleArray(banks) { if (it == 0) 1.0 else t.bank1Gain }
        private val formants = t.formants.map { (hz, db, q) -> Biquad.peaking(fs, hz, db, q) }.toTypedArray()

        fun process(input: DoubleArray): Double {
            var mix = 0.0
            for (b in 0 until banks) {
                val line = buf[b]
                val i = idx[b]
                val y = input[b] + feedback * line[i]
                line[i] = y.toFloat()
                idx[b] = if (i + 1 == line.size) 0 else i + 1
                mix += y * bankGain[b]
            }
            var s = mix.toFloat()
            for (k in formants.indices) s = formants[k].process(s)
            return s.toDouble()
        }
    }

    /** Engine character (pulse shape, path lengths, body) shaded by the exhaust part. */
    private class Timbre(
        val riseMs: Double,
        val decayMs: Double,
        val turbulence: Double,
        val staggerMs: Double,
        val bankDelayMs: Double,
        val bank1Gain: Double,
        val tailMs: DoubleArray,
        val combFeedback: Double,
        val formants: List<Triple<Double, Double, Double>>,
    ) {
        companion object {
            fun of(id: EngineId, exhaust: ExhaustPart): Timbre {
                val (riseScale, turbScale, tailScale, comb) = when (exhaust) {
                    ExhaustPart.STOCK -> listOf(1.3, 0.7, 1.35, -0.15)
                    ExhaustPart.SPORT -> listOf(1.0, 1.0, 1.0, -0.3)
                    ExhaustPart.TRACK -> listOf(0.8, 1.3, 0.8, -0.4)
                }
                return when (id) {
                    EngineId.I4 -> Timbre(
                        0.12 * riseScale, 1.3, 0.35 * turbScale, 0.07, 0.0, 1.0, doubleArrayOf(7.0 * tailScale), comb,
                        listOf(Triple(95.0, 3.0, 1.2), Triple(380.0, 4.0, 1.4), Triple(1500.0, 3.0, 1.8)),
                    )
                    EngineId.V8 -> Timbre(
                        0.15 * riseScale, 1.8, 0.3 * turbScale, 0.06, 0.6, 0.85, doubleArrayOf(7.6 * tailScale, 8.4 * tailScale), comb,
                        listOf(Triple(75.0, 5.0, 1.0), Triple(260.0, 3.0, 1.2), Triple(900.0, 2.0, 1.5)),
                    )
                    EngineId.V12 -> Timbre(
                        0.09 * riseScale, 1.0, 0.25 * turbScale, 0.05, 0.25, 0.95, doubleArrayOf(6.6 * tailScale, 6.9 * tailScale), comb,
                        listOf(Triple(120.0, 2.0, 1.0), Triple(520.0, 4.0, 1.3), Triple(2000.0, 4.0, 1.6)),
                    )
                }
            }
        }
    }

    companion object {
        private const val CYCLE_DEG = 720.0
        private const val REST_DEG = 45.0
        private const val RELAY_MS = 40.0
        private const val MAX_PULSES = 24
        private const val FADE = 2048
        private const val PULSE_END = 1e-3
        private const val OUT_GAIN = 0.5
        private const val AMP_FLOOR = 0.3
        private const val MOTORING_AMP = 0.08
        private const val JITTER_A_BASE = 0.04
        private const val JITTER_A_IDLE = 0.08
        private const val JITTER_A_MOTORING = 0.1
        private const val JITTER_T_MS = 0.04
        private const val DISP_GAIN = 0.03
        private const val DISP_T_MS = 0.06
        private const val BASE_LATENCY_MS = 0.5
        private const val REFERENCE_RPM = 1500.0
        private const val RPM_EXPONENT = 0.5
        private const val MIN_SCALE = 0.35
        private const val MAX_SCALE = 1.8
        private const val TURB_COEF = 0.45
        private val TURB_NORM = sqrt(3.0 * (2 - TURB_COEF) / TURB_COEF) // one-pole lowpassed uniform noise back to unit variance
        private const val STARTER_GAIN = 0.05f
        private const val QUIET_SAMPLES = 24_000

        /** The starter motor's whine rises a little as it gets the engine turning. */
        fun starterHz(rpm: Double) = 140.0 + 0.15 * rpm
    }
}
