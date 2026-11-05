package com.downshift.app.core.voice

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Spec 6.2: what the engine plays, cut from a cleaned take. The engine gets four 30 ms grains from its steadiest
 * 0.3 s, Hann-windowed and scaled to peak 1, fired in turn so no two firings sound alike; the whistle gets a loop from
 * its steadiest 0.4 s whose end runs into its start over 20 ms. resample() moves a material to the engine's rate.
 */
object VoiceCut {
    const val GRAINS = 4
    const val GRAIN_MS = 30.0
    private const val GRAIN_WINDOW_SEC = 0.3
    private const val LOOP_SEC = 0.4
    private const val SEAM_MS = 20.0
    private const val FRAME_MS = 10.0
    private const val LOUD_ENOUGH = 0.3 // of the loudest frame: a quiet stretch is steady too, but not what we want

    fun grains(clean: FloatArray, rate: Int): List<FloatArray> {
        val len = (GRAIN_MS * rate / 1000).toInt()
        val win = minOf(clean.size, (GRAIN_WINDOW_SEC * rate).toInt())
        require(win >= len) { "a take of ${clean.size} samples is shorter than one grain" }
        val start = steadiest(clean, win, rate)
        val step = (win - len) / (GRAINS - 1)
        return List(GRAINS) { g ->
            val from = start + g * step
            val grain = FloatArray(len) { i -> clean[from + i] * hann(i, len) }
            val peak = grain.maxOf { abs(it) }
            if (peak > 0f) for (i in grain.indices) grain[i] /= peak
            grain
        }
    }

    fun loop(clean: FloatArray, rate: Int): FloatArray {
        val win = minOf(clean.size, (LOOP_SEC * rate).toInt())
        val seam = minOf((SEAM_MS * rate / 1000).toInt(), win / 4)
        val start = steadiest(clean, win, rate)
        val s = clean.copyOfRange(start, start + win)
        val out = s.copyOf(win - seam)
        for (i in 0 until seam) {
            val w = i.toFloat() / seam
            out[i] = s[i] * w + s[win - seam + i] * (1 - w)
        }
        return out
    }

    /** Start of the [len]-sample window whose 10 ms levels vary least for their size, among loud enough windows. */
    internal fun steadiest(x: FloatArray, len: Int, rate: Int): Int {
        if (len >= x.size) return 0
        val frame = (FRAME_MS * rate / 1000).toInt()
        val levels = DoubleArray(x.size / frame) { f -> rms(x, f * frame, frame) }
        val per = maxOf(1, len / frame)
        val loudest = levels.maxOrNull() ?: 0.0
        var best = 0
        var bestScore = Double.MAX_VALUE
        for (f in 0..levels.size - per) {
            var mean = 0.0
            for (k in f until f + per) mean += levels[k]
            mean /= per
            if (mean < LOUD_ENOUGH * loudest) continue
            var v = 0.0
            for (k in f until f + per) v += (levels[k] - mean) * (levels[k] - mean)
            val score = sqrt(v / per) / mean
            if (score < bestScore) {
                bestScore = score
                best = f
            }
        }
        return (best * frame).coerceAtMost(x.size - len)
    }

    fun resample(x: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to) return x.copyOf()
        val n = (x.size.toLong() * to / from).toInt()
        val step = from.toDouble() / to
        return FloatArray(n) { i ->
            val p = i * step
            val i0 = p.toInt()
            val i1 = minOf(i0 + 1, x.size - 1)
            x[i0] + (x[i1] - x[i0]) * (p - i0).toFloat()
        }
    }

    private fun hann(i: Int, n: Int) = (0.5 - 0.5 * cos(2 * PI * i / (n - 1))).toFloat()

    private fun rms(x: FloatArray, from: Int, n: Int): Double {
        var s = 0.0
        for (i in from until from + n) s += x[i] * x[i]
        return sqrt(s / n)
    }
}
