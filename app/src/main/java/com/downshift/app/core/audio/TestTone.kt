package com.downshift.app.core.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** A stand-in engine loop for latency tests: one decaying pulse per firing, so the pitch follows the firing rate. */
object TestTone {
    /** Four-stroke: each cylinder fires once every two crank revolutions. */
    fun firingHz(rpm: Double, cylinders: Int): Double = rpm / 60.0 * cylinders / 2.0

    fun engineLoop(sampleRate: Int, firingHz: Double, periods: Int = 24): FloatArray {
        val period = (sampleRate / firingHz).toInt().coerceAtLeast(2)
        val out = FloatArray(period * periods)
        for (i in out.indices) {
            val t = (i % period).toDouble() / sampleRate
            out[i] = (0.8 * exp(-t * 60.0) * sin(2 * PI * 3.0 * firingHz * t)).toFloat()
        }
        return out
    }
}
