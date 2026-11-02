package com.downshift.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestToneTest {
    @Test
    fun firingFrequencyOfAFourStroke() {
        assertEquals(30.0, TestTone.firingHz(900.0, 4), 1e-9)
        assertEquals(240.0, TestTone.firingHz(3600.0, 8), 1e-9)
    }

    @Test
    fun loopIsAWholeNumberOfFiringPeriods() {
        val loop = TestTone.engineLoop(48_000, 30.0, periods = 4)
        assertEquals(1600 * 4, loop.size)
        assertEquals(loop[0], loop[1600], 0f)
        assertTrue(loop.all { it in -1f..1f })
    }
}
