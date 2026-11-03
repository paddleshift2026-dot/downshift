package com.downshift.app.core.trip

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

class DistanceMeterTest {
    private fun DistanceMeter.drive(mps: Double, limit: Int, sec: Double, dt: Double = 0.1) {
        repeat((sec / dt).roundToInt()) { step(mps, limit, dt) }
    }

    @Test
    fun withoutAnOdometerTheSpeedIntegralIsTheDistance() {
        val m = DistanceMeter()
        m.drive(10.0, 50, 100.0) // 1 km at 36 km/h under a 50 limit
        assertEquals(1.0, m.totalKm, 1e-6)
        assertEquals(1.0, m.knownKm, 1e-6)
        assertEquals(1.0, m.keptKm, 1e-6)
        assertEquals(0.0, m.unknownKm, 1e-6)
    }

    @Test
    fun overTheLimitIsKnownButNotKeptAndNoLimitIsUnknown() {
        val m = DistanceMeter()
        m.drive(20.0, 50, 50.0) // 1 km at 72 km/h, limit 50
        m.drive(10.0, 0, 50.0) // 0.5 km where the navi gave no limit
        assertEquals(1.5, m.totalKm, 1e-6)
        assertEquals(1.0, m.knownKm, 1e-6)
        assertEquals(0.0, m.keptKm, 1e-6)
        assertEquals(0.5, m.unknownKm, 1e-6)
    }

    @Test
    fun theSpeedIsComparedInWholeKilometresPerHour() {
        val m = DistanceMeter()
        m.drive(14.0, 50, 10.0) // 50.4 km/h reads 50: kept
        m.drive(14.1, 50, 10.0) // 50.8 km/h reads 51: not kept
        assertEquals(0.140, m.keptKm, 1e-6)
        assertEquals(0.281, m.knownKm, 1e-6)
    }

    @Test
    fun theOdometerIsTheTotalOnceItMovesAndScalesTheSplit() {
        val m = DistanceMeter()
        m.odometer(1000.0)
        m.drive(10.0, 50, 50.0) // 0.5 km kept
        m.drive(10.0, 0, 50.0) // 0.5 km unknown
        assertEquals(1.0, m.totalKm, 1e-6) // the odometer has not moved yet
        m.odometer(1001.2)
        assertEquals(1.2, m.totalKm, 1e-6)
        assertEquals(0.6, m.keptKm, 1e-6)
        assertEquals(0.6, m.unknownKm, 1e-6)
    }

    @Test
    fun odometerJumpsAndRollbacksAreIgnored() {
        val m = DistanceMeter()
        m.odometer(1000.0)
        m.odometer(1100.0) // a 100 km jump is a new reading, not distance
        m.odometer(1100.3)
        m.odometer(1100.1) // backwards: count on from here
        m.odometer(1100.4)
        assertEquals(0.6, m.totalKm, 1e-6)
    }
}
