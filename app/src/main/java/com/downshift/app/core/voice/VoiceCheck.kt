package com.downshift.app.core.voice

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

enum class VoiceVerdict { OK, SILENT, QUIET, LOUD, SHORT }

/** A take after VoiceCheck: the trimmed samples (normalised and capped when OK) and the verdict. */
class Cleaned(val samples: FloatArray, val verdict: VoiceVerdict)

/**
 * Spec 6.1: trims the silence (10 ms frames under −40 dBFS) at both ends and judges the take: nothing heard, peak
 * under −30 dBFS, 1% or more of it clipped, or shorter than 0.25 s (sustained) / 0.05 s (one-shot). A usable take
 * gets its peak set to −3 dBFS, is capped to its slot's length and fades in over 2 ms and out over 5 ms.
 */
object VoiceCheck {
    const val RATE = 48_000
    const val RECORD_SEC = 2.5
    private val FLOOR = db(-40.0)
    private val QUIET_PEAK = db(-30.0)
    private val TARGET_PEAK = db(-3.0)
    private const val CLIP = 0.99f
    private const val CLIP_SHARE = 0.01
    private const val MIN_SUSTAINED_SEC = 0.25
    private const val MIN_SHOT_SEC = 0.05
    private const val FADE_IN_MS = 2.0
    private const val FADE_OUT_MS = 5.0

    fun clean(raw: FloatArray, slot: VoiceSlot, rate: Int = RATE): Cleaned {
        val frame = rate / 100
        var first = -1
        var last = -1
        for (f in 0 until raw.size / frame) {
            if (rms(raw, f * frame, frame) > FLOOR) {
                if (first < 0) first = f
                last = f
            }
        }
        if (first < 0) return Cleaned(FloatArray(0), VoiceVerdict.SILENT)
        val cut = raw.copyOfRange(first * frame, (last + 1) * frame)
        val peak = cut.maxOf { abs(it) }
        val verdict = when {
            raw.count { abs(it) >= CLIP } >= CLIP_SHARE * raw.size -> VoiceVerdict.LOUD
            peak < QUIET_PEAK -> VoiceVerdict.QUIET
            cut.size < (if (slot.sustained) MIN_SUSTAINED_SEC else MIN_SHOT_SEC) * rate -> VoiceVerdict.SHORT
            else -> VoiceVerdict.OK
        }
        if (verdict != VoiceVerdict.OK) return Cleaned(cut, verdict)
        val n = minOf(cut.size, (slot.maxSec * rate).toInt())
        val gain = TARGET_PEAK / peak
        val out = FloatArray(n) { (cut[it] * gain).toFloat() }
        val fadeIn = (FADE_IN_MS * rate / 1000).toInt()
        val fadeOut = (FADE_OUT_MS * rate / 1000).toInt()
        for (i in 0 until minOf(fadeIn, n)) out[i] *= i.toFloat() / fadeIn
        for (i in 0 until minOf(fadeOut, n)) out[n - 1 - i] *= i.toFloat() / fadeOut
        return Cleaned(out, VoiceVerdict.OK)
    }

    private fun rms(x: FloatArray, from: Int, n: Int): Double {
        var s = 0.0
        for (i in from until from + n) s += x[i] * x[i]
        return sqrt(s / n)
    }

    private fun db(v: Double) = 10.0.pow(v / 20)
}
