package com.downshift.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LatencyStatsTest {
    @Test
    fun emptyHasNoPercentiles() {
        val s = LatencyStats()
        assertNull(s.p50())
        assertNull(s.p95())
        assertNull(s.max())
        assertEquals(0, s.count)
    }

    @Test
    fun nearestRankPercentiles() {
        val s = LatencyStats()
        (1..20).forEach { s.add(it.toDouble()) }
        assertEquals(10.0, s.p50()!!, 1e-9)
        assertEquals(19.0, s.p95()!!, 1e-9)
        assertEquals(20.0, s.max()!!, 1e-9)
    }

    @Test
    fun singleSampleIsEveryPercentile() {
        val s = LatencyStats()
        s.add(42.0)
        assertEquals(42.0, s.p50()!!, 1e-9)
        assertEquals(42.0, s.p95()!!, 1e-9)
    }
}
