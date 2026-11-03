package com.downshift.app.core.flow

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenFlowTest {
    @Test
    fun consentComesFirst() {
        val f = ScreenFlow(consented = false)
        assertEquals(Screen.ONBOARDING, f.screen)
        f.tripStarted()
        assertEquals(Screen.ONBOARDING, f.screen)
        f.agree()
        assertEquals(Screen.GARAGE, f.screen)
    }

    @Test
    fun startDriveArriveAndBackToTheGarage() {
        val f = ScreenFlow(consented = true)
        f.openStart()
        assertEquals(Screen.START, f.screen)
        f.tripStarted()
        assertEquals(Screen.DRIVE, f.screen)
        f.tripEnded(summary = true)
        assertEquals(Screen.SUMMARY, f.screen)
        f.back()
        assertEquals(Screen.GARAGE, f.screen)
    }

    @Test
    fun aShortHopReturnsWhereItBegan() {
        val f = ScreenFlow(consented = true)
        f.openStart(); f.tripStarted(); f.tripEnded(summary = false)
        assertEquals(Screen.START, f.screen)
        f.back(); f.tripStarted(); f.tripEnded(summary = false)
        assertEquals(Screen.GARAGE, f.screen)
    }

    @Test
    fun theSafetyNoteCanBeReadAgain() {
        val f = ScreenFlow(consented = true)
        f.reviewSafety()
        assertEquals(Screen.ONBOARDING, f.screen)
        f.agree()
        assertEquals(Screen.GARAGE, f.screen)
    }
}
