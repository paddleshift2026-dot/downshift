package com.downshift.app.core.sound

import kotlin.random.Random

/** One exhaust pop: a short burst of filtered noise. */
data class Pop(val gain: Double, val durationMs: Double, val cutoffHz: Double)

/** Decides when the exhaust crackles on overrun: a random pop rate that rises with intensity and rpm, from a fixed seed. */
class PopScheduler(seed: Long) {
    private val random = Random(seed)

    fun step(overrun: Boolean, rpmNorm: Double, intensity: Double, dtSec: Double): List<Pop> {
        if (!overrun || intensity <= 0.0) return emptyList()
        val rate = intensity * (BASE_RATE + RPM_RATE * rpmNorm.coerceIn(0.0, 1.0))
        if (random.nextDouble() >= rate * dtSec) return emptyList()
        return listOf(Pop(gain = between(0.3, 1.0), durationMs = between(2.0, 15.0), cutoffHz = between(1500.0, 6000.0)))
    }

    private fun between(lo: Double, hi: Double) = lo + (hi - lo) * random.nextDouble()

    private companion object {
        const val BASE_RATE = 4.0
        const val RPM_RATE = 16.0
    }
}
