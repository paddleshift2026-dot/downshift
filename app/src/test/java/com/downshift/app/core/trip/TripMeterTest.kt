package com.downshift.app.core.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripMeterTest {
    @Test
    fun pointsFollowThePrdRules() {
        val t = TripTotals(distanceKm = 18.4, knownKm = 17.2, keptKm = 16.7, unknownKm = 1.2, placesPassed = 3, placesKept = 3, turns = 22, signalled = 21)
        assertEquals(TripPoints(distance = 92, limit = 17, places = 18, signals = 11), PointRules.of(t))
        assertEquals(138, PointRules.of(t).total)
    }

    @Test
    fun aDriveEarnsEveryKindOfPoint() {
        val m = TripMeter()
        m.turn(TurnAhead(Side.LEFT, highway = false, remainM = 300.0))
        m.place(PlaceAhead("c1", PlaceKind.CAMERA, 50, 600.0))
        for (k in 1..1000) { // 1 km at 36 km/h under a 50 limit, a metre a step
            val signal = if (k in 260..300) Signal.LEFT else Signal.OFF
            m.step(TripTick(10.0, signal, 50), 0.1)
        }
        val t = m.finish()
        assertEquals(1.0, t.distanceKm, 1e-6)
        assertEquals(1.0, t.keptKm, 1e-6)
        assertEquals(1 to 1, t.placesPassed to t.placesKept)
        assertEquals(1 to 1, t.turns to t.signalled)
        assertEquals(TripPoints(5, 1, 6, 1), PointRules.of(t))
    }

    @Test
    fun aResumedTripCarriesOnFromItsCheckpoint() {
        val saved = TripTotals(distanceKm = 2.0, knownKm = 1.0, keptKm = 1.0, unknownKm = 1.0, placesPassed = 1, placesKept = 1, turns = 2, signalled = 1)
        val m = TripMeter(start = saved)
        repeat(1000) { m.step(TripTick(10.0, Signal.OFF, 50), 0.1) }
        val t = m.totals()
        assertEquals(3.0, t.distanceKm, 1e-6)
        assertEquals(2.0, t.keptKm, 1e-6)
        assertEquals(1.0, t.unknownKm, 1e-6)
        assertEquals(2 to 1, t.turns to t.signalled)
    }

    @Test
    fun theOdometerDecidesTheDistanceOnceItMoves() {
        val m = TripMeter()
        m.odometer(500.0)
        repeat(1000) { m.step(TripTick(10.0, Signal.OFF, 0), 0.1) }
        m.odometer(501.5)
        assertEquals(1.5, m.totals().distanceKm, 1e-6)
        assertEquals(1.5, m.totals().unknownKm, 1e-6)
    }

    @Test
    fun shortHopsGetNoSummary() {
        assertFalse(PointRules.showsSummary(TripTotals(distanceKm = 0.08)))
        assertTrue(PointRules.showsSummary(TripTotals(distanceKm = 0.1)))
    }

    @Test
    fun aCheckpointSurvivesTheRoundTrip() {
        val c = TripCheckpoint(startedAtMs = 1_000L, savedAtMs = 61_000L, targetId = "t3", totals = TripTotals(distanceKm = 1.5, placesPassed = 1))
        assertEquals(c, TripCodec.decode(TripCodec.encode(c)))
        assertNull(TripCodec.decode(null))
        assertNull(TripCodec.decode("{nope"))
    }
}
