package com.downshift.app.core.powertrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadEstimatorTest {
    @Test
    fun steadyCruiseGivesALightLoad() {
        val e = LoadEstimator()
        var l = 0.0
        repeat(300) { l = e.update(20.0, 0.01) }
        assertEquals(LoadEstimator.CRUISE_LOAD, l, 0.02)
    }

    @Test
    fun hardAccelerationSaturatesTheLoad() {
        val e = LoadEstimator()
        var v = 5.0
        var l = 0.0
        repeat(200) { v += 3.0 * 0.01; l = e.update(v, 0.01) }
        assertTrue("load $l", l > 0.9)
    }

    @Test
    fun decelerationReadsAsOverrun() {
        val e = LoadEstimator()
        var v = 25.0
        var l = 1.0
        repeat(200) { v -= 2.0 * 0.01; l = e.update(v, 0.01) }
        assertEquals(0.0, l, 0.02)
    }

    @Test
    fun standingStillIsZero() {
        val e = LoadEstimator()
        var l = 1.0
        repeat(100) { l = e.update(0.0, 0.01) }
        assertEquals(0.0, l, 1e-9)
    }
}
