package com.downshift.app.core.audio

/**
 * Plays a mono loop at a fractional rate with linear interpolation: rate 2.0 is an octave up, 0.5 an octave down.
 * The read position carries over between render calls, so consecutive buffers join without a seam.
 */
class LoopResampler(private val loop: FloatArray) {
    init {
        require(loop.size >= 2) { "loop needs at least 2 samples" }
    }

    var rate: Double = 1.0
        set(value) {
            field = value.coerceIn(MIN_RATE, MAX_RATE)
        }

    private var pos = 0.0

    fun render(out: FloatArray, frames: Int = out.size) {
        val n = loop.size
        for (i in 0 until frames) {
            val i0 = pos.toInt()
            val frac = (pos - i0).toFloat()
            val a = loop[i0]
            val b = loop[(i0 + 1) % n]
            out[i] = a + (b - a) * frac
            pos += rate
            if (pos >= n) pos %= n
        }
    }

    companion object {
        const val MIN_RATE = 0.25
        const val MAX_RATE = 4.0
    }
}
