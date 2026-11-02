package com.downshift.app.core.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tanh

/** Saturation normalised so ±1 maps to ±1: tanh(drive·x)/tanh(drive). The input is clipped to ±1 first. */
fun softClip(x: Float, drive: Float): Float {
    val c = x.coerceIn(-1f, 1f)
    if (drive < 0.001f) return c
    return tanh(drive * c) / tanh(drive)
}

/** The output stage: transparent up to 0.8, then a tanh knee that never passes ±1. */
fun limit(x: Float): Float {
    val m = abs(x)
    if (m <= KNEE) return x
    return sign(x) * (KNEE + (1f - KNEE) * tanh((m - KNEE) / (1f - KNEE)))
}

private const val KNEE = 0.8f

/** A gain that moves to a target in equal steps over a number of samples, so gain changes never click. */
class GainRamp(initial: Float = 0f) {
    var value: Float = initial
        private set
    private var target = initial
    private var step = 0f
    private var left = 0

    fun rampTo(target: Float, samples: Int) {
        this.target = target
        if (samples <= 0) {
            value = target
            left = 0
            return
        }
        step = (target - value) / samples
        left = samples
    }

    fun next(): Float {
        if (left > 0) {
            left--
            value = if (left == 0) target else value + step
        }
        return value
    }
}

/** A sine oscillator whose phase carries over frequency changes. */
class SineOsc(private val sampleRate: Double) {
    var freqHz: Double = 0.0
    private var phase = 0.0

    fun next(): Float {
        val y = sin(2 * PI * phase).toFloat()
        phase += freqHz / sampleRate
        if (phase >= 1.0) phase -= phase.toLong()
        return y
    }
}

/** White noise in -1..1 from a seeded xorshift32. */
class NoiseSource(seed: Long) {
    private var s = (seed xor (seed ushr 32)).toInt().let { if (it == 0) 0x9E3779B9.toInt() else it }

    fun next(): Float {
        s = s xor (s shl 13)
        s = s xor (s ushr 17)
        s = s xor (s shl 5)
        return s / 2147483648f
    }
}
