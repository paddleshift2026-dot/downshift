package com.downshift.app.core.flow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroTest {
    @Test
    fun showsOnAColdStartBeforeTheFirstScreen() {
        assertTrue(Intro.visible(elapsedMs = 0, screen = Screen.ONBOARDING, skipped = false))
        assertTrue(Intro.visible(elapsedMs = Intro.TOTAL_MS - 1, screen = Screen.GARAGE, skipped = false))
        assertFalse(Intro.visible(elapsedMs = Intro.TOTAL_MS, screen = Screen.GARAGE, skipped = false))
    }

    @Test
    fun neverCoversTheDriveScreen() {
        assertFalse(Intro.visible(elapsedMs = 0, screen = Screen.DRIVE, skipped = false))
        assertFalse(Intro.visible(elapsedMs = Intro.FADE_IN_MS, screen = Screen.DRIVE, skipped = false))
    }

    @Test
    fun aTapSkipsIt() {
        assertFalse(Intro.visible(elapsedMs = 200, screen = Screen.GARAGE, skipped = true))
    }

    @Test
    fun theWordmarkFadesInThenTheCoverFadesOut() {
        assertEquals(0f, Intro.markAlpha(0), 0.001f)
        assertEquals(0.5f, Intro.markAlpha(Intro.FADE_IN_MS / 2), 0.001f)
        assertEquals(1f, Intro.markAlpha(Intro.FADE_IN_MS), 0.001f)
        assertEquals(1f, Intro.markAlpha(Intro.TOTAL_MS), 0.001f)

        assertEquals(1f, Intro.coverAlpha(0), 0.001f)
        assertEquals(1f, Intro.coverAlpha(Intro.FADE_IN_MS + Intro.HOLD_MS), 0.001f)
        assertEquals(0.5f, Intro.coverAlpha(Intro.FADE_IN_MS + Intro.HOLD_MS + Intro.FADE_OUT_MS / 2), 0.001f)
        assertEquals(0f, Intro.coverAlpha(Intro.TOTAL_MS), 0.001f)
    }
}
