package com.downshift.app.core.pack

import com.downshift.app.core.audio.TestTone
import com.downshift.app.core.build.EngineId
import com.downshift.app.core.dsp.NoiseSource
import com.downshift.app.core.engine.EngineSpec
import com.downshift.app.core.sound.BandLayout
import com.downshift.app.core.sound.Voicing
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * An engine pack synthesised from the spec, so the app runs end to end without licensed recordings.
 * Every firing rings two or three decaying resonances plus a burst of filtered noise. Loops hold a whole number
 * of firing patterns and are written circularly, so their ends join without a seam.
 */
object SynthPack {
    const val SOURCE = "synth"

    fun build(spec: EngineSpec, sampleRate: Int = 44_100): LoadedPack {
        val timbre = Timbre.of(spec.id)
        val on = Firing(timbre, sampleRate, offLoad = false)
        val off = Firing(timbre, sampleRate, offLoad = true)
        val ladder = BandLayout.defaultRpms(spec.idleRpm, spec.redlineRpm)
        val clips = LinkedHashMap<String, FloatArray>()

        // Like a recording, each band is louder than the one below it; idle sits at IDLE_LEVEL of the top band.
        val bands = PackRules.BANDS.size
        fun level(k: Int) = ON_RMS * (IDLE_LEVEL + (1 - IDLE_LEVEL) * k / bands)
        val idleRaw = loop(spec, timbre, on, spec.idleRpm, sampleRate, seed = 1)
        val idleGain = level(0) / rms(idleRaw)
        clips["idle"] = scale(idleRaw, idleGain)
        for (k in 1..bands) {
            clips["on_$k"] = normalize(loop(spec, timbre, on, ladder[k], sampleRate, seed = 10L + k), level(k))
            clips["off_$k"] = normalize(loop(spec, timbre, off, ladder[k], sampleRate, seed = 20L + k), level(k) * OFF_LEVEL)
        }
        clips["maxRPM"] = limiterLoop(spec, timbre, on, sampleRate)
        clips["startup"] = startup(spec, timbre, on, sampleRate, idleGain)
        clips["engine_off"] = shutdown(spec, timbre, on, sampleRate, idleGain)

        val numbers = (1..bands).toList()
        val rpms = ladder.drop(1)
        val voicing = Voicing("idle", spec.idleRpm, numbers.map { "on_$it" }, rpms, numbers.map { "off_$it" }, rpms, "startup", "engine_off", "maxRPM")
        return LoadedPack(spec.id, sampleRate, clips, voicing, SOURCE)
    }

    private fun loop(spec: EngineSpec, timbre: Timbre, firing: Firing, rpm: Double, sampleRate: Int, seed: Long, seconds: Double = LOOP_SEC): FloatArray {
        val hz = TestTone.firingHz(rpm, spec.cylinders)
        val p = timbre.pattern.size
        val firings = max(p, (seconds * hz / p).roundToInt() * p)
        return circular(ceil(firings * sampleRate / hz).toInt(), firings, timbre, firing, seed)
    }

    private fun circular(n: Int, firings: Int, timbre: Timbre, firing: Firing, seed: Long): FloatArray {
        val buf = FloatArray(n)
        val noise = NoiseSource(seed)
        for (k in 0 until firings) firing.addTo(buf, (k.toLong() * n / firings).toInt(), timbre.pattern[k % timbre.pattern.size], noise, wrap = true)
        val mean = buf.average().toFloat()
        for (i in buf.indices) buf[i] -= mean
        return buf
    }

    /** The redline loop pulsed at 12 Hz, the length of six pulses so it loops cleanly. */
    private fun limiterLoop(spec: EngineSpec, timbre: Timbre, firing: Firing, sampleRate: Int): FloatArray {
        val n = (LIMITER_PULSES * sampleRate / LIMITER_HZ).roundToInt()
        val hz = TestTone.firingHz(spec.redlineRpm, spec.cylinders)
        val p = timbre.pattern.size
        val firings = max(p, (n.toDouble() / sampleRate * hz / p).roundToInt() * p)
        val buf = normalize(circular(n, firings, timbre, firing, seed = 30), ON_RMS)
        for (i in buf.indices) buf[i] *= (0.55 + 0.45 * cos(2 * PI * LIMITER_HZ * i / sampleRate)).toFloat()
        return buf
    }

    /** Cranking, the flare to 1.6 × idle and the settle, on the same timeline as the virtual powertrain start. */
    private fun startup(spec: EngineSpec, timbre: Timbre, firing: Firing, sampleRate: Int, gain: Double): FloatArray {
        val idle = spec.idleRpm
        return sweep(spec, timbre, firing, sampleRate, STARTUP_SEC, seed = 40, gain = gain) { t ->
            when {
                t < CRANK_SEC -> CRANK_RPM * t / CRANK_SEC to CRANK_FIRING
                t < FLARE_SEC -> {
                    val x = (t - CRANK_SEC) / (FLARE_SEC - CRANK_SEC)
                    CRANK_RPM + (FLARE_FACTOR * idle - CRANK_RPM) * x * x * (3 - 2 * x) to FLARE_FIRING
                }
                else -> idle + (FLARE_FACTOR - 1) * idle * exp(-(t - FLARE_SEC) / spec.fallTau) to 1.0
            }
        }.also { buf ->
            // The starter motor whines under the cranking, after the relay's short delay.
            for (i in (STARTER_DELAY_SEC * sampleRate).toInt() until (STARTER_SEC * sampleRate).toInt()) {
                val t = i.toDouble() / sampleRate
                val env = minOf(1.0, (t - STARTER_DELAY_SEC) / 0.005) * (1 - t / STARTER_SEC)
                buf[i] += (STARTER_GAIN * env * (sin(2 * PI * STARTER_HZ * t) + 0.5 * sin(4 * PI * STARTER_HZ * t))).toFloat()
            }
            fadeOut(buf, (HANDOVER_SEC * sampleRate).toInt())
            capPeak(buf)
        }
    }

    /** Firings that slow down and fade as the engine dies. */
    private fun shutdown(spec: EngineSpec, timbre: Timbre, firing: Firing, sampleRate: Int, gain: Double): FloatArray {
        val idle = spec.idleRpm
        return sweep(spec, timbre, firing, sampleRate, SHUTDOWN_SEC, seed = 50, gain = gain) { t ->
            val rpm = idle * exp(-t / SHUTDOWN_TAU)
            rpm to if (rpm < STALL_RPM) 0.0 else (rpm / idle).pow(1.5)
        }.also { fadeOut(it, (0.1 * sampleRate).toInt()); capPeak(it) }
    }

    /** Fires along an rpm curve; curve(t) gives the rpm and the firing strength at time t. */
    private inline fun sweep(
        spec: EngineSpec, timbre: Timbre, firing: Firing, sampleRate: Int, seconds: Double, seed: Long, gain: Double,
        curve: (Double) -> Pair<Double, Double>,
    ): FloatArray {
        val buf = FloatArray((seconds * sampleRate).toInt())
        val noise = NoiseSource(seed)
        var phase = 0.0
        var k = 0
        for (i in buf.indices) {
            val (rpm, strength) = curve(i.toDouble() / sampleRate)
            phase += TestTone.firingHz(rpm, spec.cylinders) / sampleRate
            if (phase >= 1.0) {
                phase -= 1.0
                if (strength > 0.0) firing.addTo(buf, i, strength * timbre.pattern[k % timbre.pattern.size], noise, wrap = false)
                k++
            }
        }
        return scale(buf, gain)
    }

    private fun rms(x: FloatArray) = sqrt(x.sumOf { it.toDouble() * it } / x.size)

    private fun normalize(x: FloatArray, target: Double) = scale(x, target / rms(x))

    private fun scale(x: FloatArray, g: Double): FloatArray {
        for (i in x.indices) x[i] = (x[i] * g).toFloat()
        return x
    }

    private fun fadeOut(x: FloatArray, samples: Int) {
        val n = minOf(samples, x.size)
        for (j in 0 until n) x[x.size - n + j] *= 1f - (j + 1).toFloat() / n
    }

    private fun capPeak(x: FloatArray) {
        val peak = x.maxOf { abs(it) }
        if (peak > PEAK) scale(x, PEAK / peak)
    }

    private const val LOOP_SEC = 0.5
    private const val ON_RMS = 0.18
    private const val IDLE_LEVEL = 0.55
    private const val OFF_LEVEL = 0.7
    private const val PEAK = 0.95
    private const val LIMITER_HZ = 12.0
    private const val LIMITER_PULSES = 6
    private const val STARTUP_SEC = 2.0
    private const val CRANK_SEC = 0.25
    private const val CRANK_RPM = 250.0
    private const val CRANK_FIRING = 0.35
    private const val FLARE_SEC = 0.45
    private const val FLARE_FACTOR = 1.6
    private const val FLARE_FIRING = 1.2
    private const val HANDOVER_SEC = 0.2
    private const val STARTER_DELAY_SEC = 0.04
    private const val STARTER_SEC = 0.35
    private const val STARTER_HZ = 170.0
    private const val STARTER_GAIN = 0.05
    private const val SHUTDOWN_SEC = 1.2
    private const val SHUTDOWN_TAU = 0.3
    private const val STALL_RPM = 120.0
}

/** Per-engine colour: resonance frequencies, gains and decay rates (1/s), noise level and the firing-strength cycle. */
private class Timbre(val freqs: DoubleArray, val gains: DoubleArray, val decays: DoubleArray, val noise: Double, val pattern: DoubleArray) {
    companion object {
        fun of(id: EngineId) = when (id) {
            EngineId.I4 -> Timbre(doubleArrayOf(110.0, 380.0, 1150.0), doubleArrayOf(1.0, 0.6, 0.25), doubleArrayOf(35.0, 70.0, 160.0), 0.18, doubleArrayOf(1.0, 0.94, 0.98, 0.92))
            // Cross-plane: firings alternate between the banks unevenly, which is the burble.
            EngineId.V8 -> Timbre(doubleArrayOf(85.0, 260.0, 820.0), doubleArrayOf(1.0, 0.7, 0.22), doubleArrayOf(26.0, 55.0, 130.0), 0.14, doubleArrayOf(1.0, 0.75, 1.0, 0.85))
            EngineId.V12 -> Timbre(
                doubleArrayOf(150.0, 540.0, 1650.0), doubleArrayOf(0.8, 0.6, 0.35), doubleArrayOf(42.0, 85.0, 190.0), 0.10,
                doubleArrayOf(1.0, 0.97, 0.99, 0.95, 1.0, 0.98, 0.96, 0.99, 0.97, 1.0, 0.98, 0.96),
            )
        }
    }
}

/** One firing: the resonant ring is computed once, the noise burst is fresh each time. Off-load has less body and more hiss. */
private class Firing(timbre: Timbre, sampleRate: Int, offLoad: Boolean) {
    private val shape: FloatArray
    private val attack = (0.001 * sampleRate).toInt().coerceAtLeast(1)
    private val noiseGain = timbre.noise * if (offLoad) 2.2 else 1.0
    private val noiseLen = (0.025 * sampleRate).toInt()
    private val noiseDecay = exp(-1.0 / (0.006 * sampleRate))
    private val noiseCoef = 1 - exp(-2 * PI * (if (offLoad) 5500.0 else 2500.0) / sampleRate)

    init {
        val n = minOf(0.25, 6.0 / timbre.decays.min()).times(sampleRate).toInt()
        val taper = (0.005 * sampleRate).toInt()
        shape = FloatArray(n) { j ->
            val t = j.toDouble() / sampleRate
            var y = 0.0
            for (r in timbre.freqs.indices) {
                val g = timbre.gains[r] * if (offLoad && r == 0) 0.6 else 1.0
                y += g * exp(-t * timbre.decays[r]) * sin(2 * PI * timbre.freqs[r] * t)
            }
            val edge = minOf(1.0, j.toDouble() / attack, (n - 1 - j).toDouble() / taper)
            (y * edge).toFloat()
        }
    }

    /** Adds one firing at pos; with wrap the tail continues from the start of buf, for loops. */
    fun addTo(buf: FloatArray, pos: Int, amp: Double, noise: NoiseSource, wrap: Boolean) {
        val n = buf.size
        val a = amp.toFloat()
        for (j in shape.indices) {
            val i = pos + j
            if (i >= n && !wrap) break
            buf[i % n] += a * shape[j]
        }
        var lp = 0.0
        var env = 1.0
        for (j in 0 until noiseLen) {
            lp += noiseCoef * (noise.next() - lp)
            val i = pos + j
            if (i >= n && !wrap) break
            buf[i % n] += (amp * noiseGain * lp * env * minOf(1.0, j.toDouble() / attack)).toFloat()
            env *= noiseDecay
        }
    }
}
