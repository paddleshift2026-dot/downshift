package com.downshift.app.core.powertrain

import com.downshift.app.core.build.TransmissionPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransmissionTest {
    @Test
    fun coupledRpmFollowsWheelSpeed() {
        // 30 m/s on a 0.33 m wheel = 868 wheel rpm; × ratio 1.0 × final drive 3.2
        assertEquals(2778.0, coupledRpm(30.0, 1.0, 3.2), 2.0)
        assertEquals(0.0, coupledRpm(0.0, 4.7, 3.2), 1e-9)
    }

    @Test
    fun gearRatiosDescendForEveryTransmission() {
        TransmissionPart.entries.forEach { p ->
            assertTrue("$p", Transmissions.of(p).ratios.zipWithNext().all { (a, b) -> a > b })
        }
    }

    @Test
    fun shiftPointsRiseWithThrottleAndLeaveAGap() {
        val redline = 6800.0
        assertTrue(ShiftSchedule.upshiftRpm(1.0, redline) > ShiftSchedule.upshiftRpm(0.1, redline))
        assertTrue(ShiftSchedule.upshiftRpm(1.0, redline) <= 0.96 * redline)
        listOf(0.0, 0.5, 1.0).forEach { t ->
            assertTrue("t=$t", ShiftSchedule.downshiftRpm(t, redline) < 0.7 * ShiftSchedule.upshiftRpm(t, redline))
        }
    }
}
