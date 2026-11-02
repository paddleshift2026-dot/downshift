package com.downshift.app.core.audio

import kotlin.math.ceil

/** Nearest-rank percentiles of latency samples in milliseconds. */
class LatencyStats {
    private val samples = ArrayList<Double>()

    @Synchronized
    fun add(ms: Double) {
        samples.add(ms)
    }

    val count: Int
        @Synchronized get() = samples.size

    @Synchronized
    fun percentile(p: Double): Double? {
        if (samples.isEmpty()) return null
        val sorted = samples.sorted()
        val rank = ceil(p * sorted.size / 100.0 - 1e-9).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    fun p50() = percentile(50.0)
    fun p95() = percentile(95.0)

    @Synchronized
    fun max(): Double? = samples.maxOrNull()

    @Synchronized
    fun clear() = samples.clear()
}
