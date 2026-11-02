package com.downshift.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GearStateTest {
    @Test
    fun aaosSelectorValuesMapToGearStates() {
        assertEquals(GearState.NEUTRAL, GearState.fromAaos(1))
        assertEquals(GearState.REVERSE, GearState.fromAaos(2))
        assertEquals(GearState.PARK, GearState.fromAaos(4))
        assertEquals(GearState.DRIVE, GearState.fromAaos(8))
    }

    @Test
    fun manualGearsCountAsDriveAndTheRestIsUnknown() {
        assertEquals(GearState.DRIVE, GearState.fromAaos(16))
        assertEquals(GearState.DRIVE, GearState.fromAaos(0x1000))
        assertEquals(GearState.UNKNOWN, GearState.fromAaos(0))
        assertEquals(GearState.UNKNOWN, GearState.fromAaos(3))
    }
}
