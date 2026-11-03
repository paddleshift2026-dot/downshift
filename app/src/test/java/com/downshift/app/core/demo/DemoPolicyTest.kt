package com.downshift.app.core.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoPolicyTest {
    @Test
    fun injectionsThatChangePointsNeedDemoMode() {
        for (e in listOf("limit", "camera", "section", "school", "turn", "lapse")) {
            assertFalse(e, DemoPolicy.allowsDrive(e, demo = false))
            assertTrue(e, DemoPolicy.allowsDrive(e, demo = true))
        }
    }

    @Test
    fun theFlowAndTheSoundAreAlwaysReachable() {
        for (e in listOf("agree", "screen", "guide", "music", "mute", "quiet", "pleos", "route")) {
            assertTrue(e, DemoPolicy.allowsDrive(e, demo = false))
        }
    }

    @Test
    fun pointsFromNothingNeedDemoModeButTapsDoNot() {
        assertFalse(DemoPolicy.allowsGarage("add", demo = false))
        assertFalse(DemoPolicy.allowsGarage("reset", demo = false))
        assertTrue(DemoPolicy.allowsGarage("add", demo = true))
        for (a in listOf("unlock", "equip", "preview", "tab")) assertTrue(a, DemoPolicy.allowsGarage(a, demo = false))
    }

    @Test
    fun theLapseIsADemoToolOnly() {
        assertEquals(1, DemoPolicy.lapse(20, demo = false))
        assertEquals(20, DemoPolicy.lapse(20, demo = true))
        assertEquals(1, DemoPolicy.lapse(0, demo = true))
        assertEquals(DemoPolicy.MAX_LAPSE, DemoPolicy.lapse(999, demo = true))
    }
}
