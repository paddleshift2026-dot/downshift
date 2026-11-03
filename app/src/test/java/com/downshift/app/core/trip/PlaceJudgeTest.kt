package com.downshift.app.core.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class PlaceJudgeTest {
    private fun PlaceJudge.drive(mps: Double, metres: Double, dt: Double = 0.1) {
        repeat((metres / (mps * dt)).roundToInt()) { step(mps, dt) }
    }

    @Test
    fun aCameraIsJudgedOnTheLastFiftyMetres() {
        val j = PlaceJudge()
        j.announce(PlaceAhead("c1", PlaceKind.CAMERA, 50, 200.0))
        j.drive(20.0, 140.0) // 72 km/h, still 60 m out
        j.drive(11.0, 66.0) // 40 km/h past the camera
        j.announce(PlaceAhead("c2", PlaceKind.CAMERA, 50, 200.0))
        j.drive(20.0, 160.0) // 72 km/h into the last 50 m
        j.drive(11.0, 66.0)
        assertEquals(2, j.passed)
        assertEquals(1, j.kept)
    }

    @Test
    fun aSchoolZoneQuietsFromAHundredMetresAndIsKeptAtThirty() {
        val j = PlaceJudge()
        j.announce(PlaceAhead("z1", PlaceKind.SCHOOL_ZONE, 0, 150.0))
        assertFalse(j.schoolZone)
        j.drive(8.0, 60.0) // 90 m to go
        assertTrue(j.schoolZone)
        j.drive(8.0, 300.0) // in, and 210 m along at 29 km/h
        assertTrue(j.schoolZone)
        assertEquals(0, j.passed)
        j.drive(8.0, 100.0) // out at the far end
        assertFalse(j.schoolZone)
        assertEquals(1, j.passed)
        assertEquals(1, j.kept)
    }

    @Test
    fun fortyInASchoolZoneIsPassedButNotKept() {
        val j = PlaceJudge()
        j.announce(PlaceAhead("z1", PlaceKind.SCHOOL_ZONE, 0, 50.0))
        j.drive(11.2, 400.0) // 40 km/h all the way through
        assertEquals(1, j.passed)
        assertEquals(0, j.kept)
    }

    @Test
    fun aSectionIsJudgedOnItsAverageSpeed() {
        val j = PlaceJudge()
        j.announce(PlaceAhead("s1", PlaceKind.SECTION_START, 80, 10.0))
        j.announce(PlaceAhead("s2", PlaceKind.SECTION_END, 80, 1010.0))
        j.drive(25.0, 400.0) // 90 km/h for the first 390 m of the section
        j.drive(17.0, 640.0) // 61 km/h for the rest: about 70 km/h on average
        j.announce(PlaceAhead("s3", PlaceKind.SECTION_START, 80, 10.0))
        j.announce(PlaceAhead("s4", PlaceKind.SECTION_END, 80, 1010.0))
        j.drive(25.0, 1050.0) // 90 km/h throughout
        assertEquals(2, j.passed)
        assertEquals(1, j.kept)
    }

    @Test
    fun repeatsAndPlacesAlreadyPassedCountOnce() {
        val j = PlaceJudge()
        j.announce(PlaceAhead("c1", PlaceKind.CAMERA, 50, 100.0))
        j.drive(10.0, 50.0)
        j.announce(PlaceAhead("c1", PlaceKind.CAMERA, 50, 52.0)) // the navi refreshes the distance
        j.drive(10.0, 60.0)
        j.announce(PlaceAhead("c1", PlaceKind.CAMERA, 50, 0.0)) // and repeats it once passed
        j.drive(10.0, 20.0)
        j.announce(PlaceAhead("c9", PlaceKind.CAMERA, 0, 10.0)) // no limit given: not counted
        j.drive(10.0, 20.0)
        assertEquals(1, j.passed)
        assertEquals(1, j.kept)
    }

    @Test
    fun finishingJudgesAZoneWeAreInAndDropsAnOpenSection() {
        val j = PlaceJudge()
        j.announce(PlaceAhead("z1", PlaceKind.SCHOOL_ZONE, 30, 10.0))
        j.announce(PlaceAhead("s1", PlaceKind.SECTION_START, 80, 20.0))
        j.drive(8.0, 120.0)
        j.finish()
        assertEquals(1, j.passed)
        assertEquals(1, j.kept)
        assertFalse(j.schoolZone)
    }

    @Test
    fun placeTypesMapFromTheNavi() {
        assertEquals(PlaceKind.CAMERA, PlaceKinds.camera("SPEED_CAMERA_FIX"))
        assertEquals(PlaceKind.CAMERA, PlaceKinds.camera("SPEED_CAMERA_SCHOOL_ZONE"))
        assertEquals(PlaceKind.SECTION_START, PlaceKinds.camera("SPEED_CAMERA_SECTION_START"))
        assertEquals(PlaceKind.SECTION_END, PlaceKinds.camera("SPEED_CAMERA_SECTION_END"))
        assertNull(PlaceKinds.camera("SECTION_SPEED_ALERT"))
        assertEquals(PlaceKind.SCHOOL_ZONE, PlaceKinds.caution("SCHOOL_ZONE"))
        assertNull(PlaceKinds.caution("SPEED_BUMP"))
    }
}
