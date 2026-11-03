package com.downshift.app.core.powertrain

import com.downshift.app.core.model.GearState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoVehicleModelTest {
    @Test
    fun fullThrottleReaches100KphLikeAHotHatch() {
        val m = DemoVehicleModel()
        var t = 0.0
        while (m.speedMps < 27.78 && t < 20.0) { m.step(1.0, 0.0, GearState.DRIVE, 0.01); t += 0.01 }
        assertTrue("0-100 km/h in $t s", t in 5.0..10.0)
    }

    @Test
    fun speedStaysUnderTopSpeed() {
        val m = DemoVehicleModel()
        repeat(10_000) { m.step(1.0, 0.0, GearState.DRIVE, 0.01) }
        assertTrue(m.speedMps <= m.topSpeedMps + 1e-6)
    }

    @Test
    fun brakingStopsTheCar() {
        val m = DemoVehicleModel(initialMps = 27.78)
        var t = 0.0
        while (m.speedMps > 0.0 && t < 10.0) { m.step(0.0, 1.0, GearState.DRIVE, 0.01); t += 0.01 }
        assertTrue("stopped after $t s", t < 5.0)
        assertEquals(0.0, m.speedMps, 1e-9)
    }

    @Test
    fun coastingSlowsGently() {
        val m = DemoVehicleModel(initialMps = 27.78)
        repeat(500) { m.step(0.0, 0.0, GearState.DRIVE, 0.01) }
        assertTrue("${m.speedMps}", m.speedMps in 23.0..27.7)
    }

    @Test
    fun parkedCarDoesNotMove() {
        val m = DemoVehicleModel()
        repeat(300) { m.step(1.0, 0.0, GearState.PARK, 0.01) }
        assertEquals(0.0, m.speedMps, 1e-9)
    }

    @Test
    fun reverseIsCappedAtWalkingPace() {
        val m = DemoVehicleModel()
        repeat(1000) { m.step(1.0, 0.0, GearState.REVERSE, 0.01) }
        assertTrue(m.speedMps <= 10.0 / 3.6 + 1e-6)
    }

    @Test
    fun haltStopsTheCarAtOnce() {
        val m = DemoVehicleModel(initialMps = 20.0)
        m.halt()
        assertEquals(0.0, m.speedMps, 0.0)
        m.step(0.0, 0.0, GearState.DRIVE, 0.1)
        assertEquals(0.0, m.speedMps, 0.0)
    }
}
