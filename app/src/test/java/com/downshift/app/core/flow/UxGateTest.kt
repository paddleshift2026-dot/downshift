package com.downshift.app.core.flow

import com.downshift.app.core.model.UxState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UxGateTest {
    private val idling = UxState(requiresDo = true, active = 0x50) // the emulator in N or D at 0 km/h: no video, no setup

    @Test
    fun aParkedCarShowsEveryScreen() {
        Screen.entries.forEach { assertTrue(it.name, UxGate.shows(it, UxState.NONE)) }
    }

    @Test
    fun doLeavesOnlyTheDriveScreen() {
        assertEquals(listOf(Screen.DRIVE), Screen.entries.filter { UxGate.shows(it, idling) })
        assertEquals(listOf(Screen.DRIVE), Screen.entries.filter { UxGate.shows(it, UxState(requiresDo = true, active = 0x1ff)) })
    }

    @Test
    fun theGarageWaitsForAStillCarWithNoDo() {
        assertTrue(UxGate.parked(stopped = true, ux = UxState.NONE))
        assertFalse(UxGate.parked(stopped = true, ux = idling)) // neutral at a standstill
        assertFalse(UxGate.parked(stopped = false, ux = UxState.NONE))
    }
}
