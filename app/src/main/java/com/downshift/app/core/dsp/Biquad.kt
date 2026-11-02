package com.downshift.app.core.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A second-order IIR filter in transposed direct form II, coefficients normalised by a0.
 * The factories follow the RBJ Audio EQ Cookbook; shelves use slope S = 1.
 */
class Biquad(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) {
    private val b0 = b0 / a0
    private val b1 = b1 / a0
    private val b2 = b2 / a0
    private val a1 = a1 / a0
    private val a2 = a2 / a0
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        // A tail decaying through silence turns denormal, which is very slow on x86 (the emulator).
        if (abs(z1) < DENORMAL) z1 = 0.0
        if (abs(z2) < DENORMAL) z2 = 0.0
        return y.toFloat()
    }

    fun processBlock(buf: FloatArray, n: Int) {
        for (i in 0 until n) buf[i] = process(buf[i])
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    /** |H(e^jω)| in dB at freqHz. */
    fun gainDb(freqHz: Double, sampleRate: Double): Double {
        val w = 2 * PI * freqHz / sampleRate
        val c1 = cos(w)
        val s1 = sin(w)
        val c2 = cos(2 * w)
        val s2 = sin(2 * w)
        val numRe = b0 + b1 * c1 + b2 * c2
        val numIm = -(b1 * s1 + b2 * s2)
        val denRe = 1 + a1 * c1 + a2 * c2
        val denIm = -(a1 * s1 + a2 * s2)
        return 10 * log10((numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm))
    }

    companion object {
        private const val DENORMAL = 1e-20

        fun lowShelf(fs: Double, f0: Double, gainDb: Double): Biquad {
            val a = 10.0.pow(gainDb / 40)
            val w0 = 2 * PI * f0 / fs
            val c = cos(w0)
            val k = 2 * sqrt(a) * sin(w0) / sqrt(2.0)
            return Biquad(
                a * ((a + 1) - (a - 1) * c + k),
                2 * a * ((a - 1) - (a + 1) * c),
                a * ((a + 1) - (a - 1) * c - k),
                (a + 1) + (a - 1) * c + k,
                -2 * ((a - 1) + (a + 1) * c),
                (a + 1) + (a - 1) * c - k,
            )
        }

        fun highShelf(fs: Double, f0: Double, gainDb: Double): Biquad {
            val a = 10.0.pow(gainDb / 40)
            val w0 = 2 * PI * f0 / fs
            val c = cos(w0)
            val k = 2 * sqrt(a) * sin(w0) / sqrt(2.0)
            return Biquad(
                a * ((a + 1) + (a - 1) * c + k),
                -2 * a * ((a - 1) + (a + 1) * c),
                a * ((a + 1) + (a - 1) * c - k),
                (a + 1) - (a - 1) * c + k,
                2 * ((a - 1) - (a + 1) * c),
                (a + 1) - (a - 1) * c - k,
            )
        }

        fun peaking(fs: Double, f0: Double, gainDb: Double, q: Double): Biquad {
            val a = 10.0.pow(gainDb / 40)
            val w0 = 2 * PI * f0 / fs
            val alpha = sin(w0) / (2 * q)
            val c = cos(w0)
            return Biquad(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
        }

        fun lowPass(fs: Double, f0: Double, q: Double = 0.7071): Biquad {
            val w0 = 2 * PI * f0 / fs
            val alpha = sin(w0) / (2 * q)
            val c = cos(w0)
            return Biquad((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
        }

        /** Band-pass with 0 dB at the centre. */
        fun bandPass(fs: Double, f0: Double, q: Double): Biquad {
            val w0 = 2 * PI * f0 / fs
            val alpha = sin(w0) / (2 * q)
            val c = cos(w0)
            return Biquad(alpha, 0.0, -alpha, 1 + alpha, -2 * c, 1 - alpha)
        }
    }
}
