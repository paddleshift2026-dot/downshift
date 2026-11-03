package com.downshift.app.core.dsp

import com.downshift.app.core.audio.LoopResampler
import com.downshift.app.core.build.ExhaustPart
import com.downshift.app.core.sound.OneShot
import com.downshift.app.core.sound.Pop
import com.downshift.app.core.sound.SoundPlan
import com.downshift.app.core.synth.EngineVoice
import com.downshift.app.core.synth.PulseDrive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.tan

/**
 * Renders SoundPlans block by block. Every clip has a persistent loop voice (its phase carries on), gains ramp
 * across each block, and the exhaust part is a filter chain that crossfades when it changes.
 *
 * Signal flow: (loops + pops) × master + one-shots → exhaust chain → + induction × master → limiter.
 * Clips recorded at clipRate are resampled to the output sampleRate (devices often run at 48 kHz, packs at 44.1 kHz).
 * Buffers and voices are allocated up front; render does not allocate unless the exhaust part changes
 * or a block is longer than any before it.
 */
class EngineMixer(sampleRate: Int, clips: Map<String, FloatArray>, seed: Long = 1, clipRate: Int = sampleRate) {
    private val fs = sampleRate.toDouble()
    private var clipRateHz = clipRate
    private var clipStep = clipRate / fs
    private var clipMap: Map<String, FloatArray> = emptyMap()
    private var pulseVoice: EngineVoice? = null
    private var voiceByName = HashMap<String, LoopVoiceState>()
    private var voices = ArrayList<LoopVoiceState>()
    private val shots = Array(MAX_SHOTS) { ShotVoice() }
    private val pops = Array(MAX_POPS) { PopVoice() }
    private val noise = NoiseSource(seed)
    private val master = GainRamp(0f)

    private var exhaust: ExhaustPart? = null
    private var chain: ExhaustChain? = null
    private var oldChain: ExhaustChain? = null
    private var fadeLeft = 0

    private var bus = FloatArray(0)
    private var scratch = FloatArray(0)
    private var spare = FloatArray(0)
    private var masterBuf = FloatArray(0)

    private val whistle = SineOsc(fs)
    private val whistleGain = GainRamp(0f)
    private val whistleAir = Svf()
    private val whine = SineOsc(fs)
    private val whine2 = SineOsc(fs)
    private val whineGain = GainRamp(0f)
    private val blowOffFilter = Svf().apply { tune(fs, BLOW_OFF_HZ, BLOW_OFF_Q) }
    private val blowOffDecay = exp(-1.0 / (BLOW_OFF_TAU * fs)).toFloat()
    private var blowOffEnv = 0f

    init {
        setClips(clips, clipRate)
    }

    /** Swaps the clip set (a new engine pack). Loop phases restart and one-shots stop. */
    fun setClips(clips: Map<String, FloatArray>, clipRate: Int = clipRateHz) {
        clipRateHz = clipRate
        clipStep = clipRate / fs
        clipMap = clips
        val byName = HashMap<String, LoopVoiceState>()
        for ((name, data) in clips) if (data.size >= 2) byName[name] = LoopVoiceState(data)
        voiceByName = byName
        voices = ArrayList(byName.values)
        for (s in shots) s.clip = null
    }

    /** The real-time exhaust voice of a procedural engine, or null for recorded loops. */
    fun setVoice(voice: EngineVoice?) {
        pulseVoice = voice
    }

    fun render(plan: SoundPlan, out: FloatArray, frames: Int) {
        ensureCapacity(frames)
        java.util.Arrays.fill(bus, 0, frames, 0f)
        renderLoops(plan, frames)
        pulseVoice?.let { v ->
            // Rendered even without a plan drive so pulses already in flight ring out.
            v.render(plan.pulse ?: PulseDrive.OFF, scratch, frames)
            for (i in 0 until frames) bus[i] += scratch[i]
        }
        startPops(plan.pops, frames)
        renderPops(frames)
        master.rampTo(plan.master.toFloat(), frames)
        for (i in 0 until frames) {
            val m = master.next()
            masterBuf[i] = m
            bus[i] *= m
        }
        startShots(plan.shots)
        renderShots(frames)
        applyExhaust(plan.exhaust, frames)
        renderInduction(plan, frames)
        for (i in 0 until frames) out[i] = limit(bus[i])
    }

    private fun renderLoops(plan: SoundPlan, frames: Int) {
        for (i in voices.indices) voices[i].target = 0f
        val loops = plan.loops
        for (i in loops.indices) {
            val l = loops[i]
            val v = voiceByName[l.clip] ?: continue
            v.target = (l.gain * plan.loudness).toFloat()
            v.targetRate = l.rate
        }
        for (k in voices.indices) {
            val v = voices[k]
            if (v.target == 0f && v.gain.value == 0f) continue
            if (v.gain.value == 0f) v.rate = v.targetRate
            v.gain.rampTo(v.target, frames)
            // The pitch glides across the block in short steps instead of jumping at its start.
            val from = v.rate
            var done = 0
            while (done < frames) {
                val n = minOf(RATE_STEP, frames - done)
                v.resampler.rate = (from + (v.targetRate - from) * (done + n) / frames) * clipStep
                v.resampler.render(scratch, n, done)
                done += n
            }
            v.rate = v.targetRate
            for (i in 0 until frames) bus[i] += scratch[i] * v.gain.next()
        }
    }

    private fun startPops(list: List<Pop>, frames: Int) {
        for (i in list.indices) {
            val p = list[i]
            val slot = pops.firstOrNull { it.left == 0 } ?: return
            val n = (p.durationMs / 1000 * fs).toInt().coerceAtLeast(1)
            slot.left = n
            slot.delay = (abs(noise.next()) * frames).toInt()
            slot.gain = (p.gain * POP_LEVEL).toFloat()
            slot.env = 1f
            slot.decay = exp(-4.6 / n).toFloat()
            slot.coef = (1 - exp(-2 * PI * p.cutoffHz / fs)).toFloat()
            slot.lp = 0f
        }
    }

    private fun renderPops(frames: Int) {
        for (v in pops) {
            if (v.left == 0) continue
            var i = minOf(v.delay, frames)
            v.delay -= i
            while (i < frames && v.left > 0) {
                v.lp += v.coef * (noise.next() - v.lp)
                bus[i] += v.lp * v.env * v.gain
                v.env *= v.decay
                v.left--
                i++
            }
        }
    }

    private fun startShots(list: List<OneShot>) {
        for (i in list.indices) {
            val s = list[i]
            val clip = clipMap[s.clip] ?: continue
            val slot = shots.firstOrNull { it.clip == null } ?: shots.maxBy { it.pos }
            slot.clip = clip
            slot.pos = 0.0
            slot.gain = s.gain.toFloat()
        }
    }

    private fun renderShots(frames: Int) {
        for (v in shots) {
            val clip = v.clip ?: continue
            val last = clip.size - 1
            var i = 0
            while (i < frames) {
                val i0 = v.pos.toInt()
                if (i0 > last) break
                val a = clip[i0]
                val b = if (i0 < last) clip[i0 + 1] else a
                bus[i] += (a + (b - a) * (v.pos - i0).toFloat()) * v.gain
                v.pos += clipStep
                i++
            }
            if (v.pos.toInt() > last) v.clip = null
        }
    }

    private fun applyExhaust(part: ExhaustPart, frames: Int) {
        if (part != exhaust) {
            val first = chain == null
            oldChain = chain
            chain = ExhaustChain(fs, part)
            exhaust = part
            fadeLeft = if (first) 0 else EXHAUST_FADE
        }
        val now = chain!!
        val old = oldChain
        if (old == null || fadeLeft == 0) {
            now.process(bus, frames)
            oldChain = null
            return
        }
        System.arraycopy(bus, 0, spare, 0, frames)
        old.process(spare, frames)
        now.process(bus, frames)
        for (i in 0 until frames) {
            val w = if (fadeLeft > 0) 1f - fadeLeft--.toFloat() / EXHAUST_FADE else 1f
            bus[i] = spare[i] * (1f - w) + bus[i] * w
        }
        if (fadeLeft == 0) oldChain = null
    }

    /** Turbo whistle (tone plus breathy noise), supercharger whine (tone plus its octave) and the blow-off hiss. */
    private fun renderInduction(plan: SoundPlan, frames: Int) {
        val s = plan.synth
        if (plan.blowOff) blowOffEnv = 1f
        whistleGain.rampTo((s.whistleGain * INDUCTION_LEVEL).toFloat(), frames)
        whineGain.rampTo((s.whineGain * INDUCTION_LEVEL).toFloat(), frames)
        if (s.whistleHz > 0) {
            whistle.freqHz = s.whistleHz
            whistleAir.tune(fs, s.whistleHz, WHISTLE_Q)
        }
        if (s.whineHz > 0) {
            whine.freqHz = s.whineHz
            whine2.freqHz = 2 * s.whineHz
        }
        val whistling = whistleGain.value > 0f || s.whistleGain > 0
        val whining = whineGain.value > 0f || s.whineGain > 0
        if (!whistling && !whining && blowOffEnv < BLOW_OFF_FLOOR) return
        for (i in 0 until frames) {
            var y = 0f
            val wg = whistleGain.next()
            if (wg > 0f) y += wg * (0.7f * whistle.next() + 0.3f * whistleAir.bandPass(noise.next()))
            val hg = whineGain.next()
            if (hg > 0f) y += hg * (whine.next() + 0.5f * whine2.next())
            if (blowOffEnv >= BLOW_OFF_FLOOR) {
                y += BLOW_OFF_LEVEL * blowOffEnv * blowOffFilter.bandPass(noise.next())
                blowOffEnv *= blowOffDecay
            }
            bus[i] += y * masterBuf[i]
        }
    }

    private fun ensureCapacity(frames: Int) {
        if (bus.size >= frames) return
        bus = FloatArray(frames)
        scratch = FloatArray(frames)
        spare = FloatArray(frames)
        masterBuf = FloatArray(frames)
    }

    private class LoopVoiceState(clip: FloatArray) {
        val resampler = LoopResampler(clip)
        val gain = GainRamp(0f)
        var target = 0f
        var rate = 1.0
        var targetRate = 1.0
    }

    private class ShotVoice {
        var clip: FloatArray? = null
        var pos = 0.0
        var gain = 0f
    }

    private class PopVoice {
        var left = 0
        var delay = 0
        var gain = 0f
        var env = 0f
        var decay = 0f
        var coef = 0f
        var lp = 0f
    }

    /** A band-pass that can be retuned every block without allocating (topology-preserving state variable filter). */
    private class Svf {
        private var a1 = 0f
        private var a2 = 0f
        private var a3 = 0f
        private var k = 1f
        private var ic1 = 0f
        private var ic2 = 0f

        fun tune(fs: Double, hz: Double, q: Double) {
            val g = tan(PI * hz.coerceIn(20.0, 0.45 * fs) / fs)
            val kk = 1 / q
            val d = 1 / (1 + g * (g + kk))
            a1 = d.toFloat()
            a2 = (g * d).toFloat()
            a3 = (g * g * d).toFloat()
            k = kk.toFloat()
        }

        /** Unity gain at the centre. */
        fun bandPass(x: Float): Float {
            val v3 = x - ic2
            val v1 = a1 * ic1 + a2 * v3
            val v2 = ic2 + a2 * ic1 + a3 * v3
            ic1 = 2 * v1 - ic1
            ic2 = 2 * v2 - ic2
            return v1 * k
        }
    }

    private companion object {
        const val MAX_SHOTS = 4
        const val MAX_POPS = 8
        const val RATE_STEP = 64
        const val EXHAUST_FADE = 2048
        const val POP_LEVEL = 0.8
        const val INDUCTION_LEVEL = 0.12
        const val WHISTLE_Q = 6.0
        const val BLOW_OFF_HZ = 2200.0
        const val BLOW_OFF_Q = 0.9
        const val BLOW_OFF_TAU = 0.1
        const val BLOW_OFF_LEVEL = 0.35f
        const val BLOW_OFF_FLOOR = 1e-4f
    }
}
