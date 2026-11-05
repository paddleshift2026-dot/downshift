package com.downshift.app.core.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class VoiceCutTest {
    private val fs = 48_000
    private fun tone(sec: Double, amp: Double, hz: Double = 200.0) = FloatArray((sec * fs).toInt()) { (amp * sin(2 * PI * hz * it / fs)).toFloat() }

    @Test
    fun fourWindowedGrainsOf30ms() {
        val g = VoiceCut.grains(tone(1.0, 0.5), fs)
        assertEquals(4, g.size)
        g.forEach {
            assertEquals((0.03 * fs).toInt(), it.size)
            assertEquals(0f, it.first(), 1e-3f)
            assertEquals(0f, it.last(), 1e-3f)
            assertEquals(1f, it.maxOf { v -> abs(v) }, 1e-4f)
        }
    }

    @Test
    fun theSteadiestStretchIsPicked() {
        val shaky = FloatArray(fs / 2) { ((0.35 + 0.25 * sin(2 * PI * 5.0 * it / fs)) * sin(2 * PI * 200.0 * it / fs)).toFloat() }
        val at = VoiceCut.steadiest(shaky + tone(0.5, 0.4), (0.3 * fs).toInt(), fs)
        assertTrue("picked $at", at >= fs / 2 - fs / 100)
    }

    @Test
    fun theLoopRunsIntoItsStartWithoutAJump() {
        val loop = VoiceCut.loop(tone(1.0, 0.5), fs)
        assertEquals((0.4 * fs).toInt() - (0.02 * fs).toInt(), loop.size)
        assertTrue(abs(loop.first() - loop.last()) < 0.05f)
    }

    @Test
    fun resamplingHalvesOrKeepsTheLength() {
        val x = tone(0.1, 0.5)
        assertEquals(x.size / 2, VoiceCut.resample(x, 48_000, 24_000).size)
        assertArrayEquals(x, VoiceCut.resample(x, 48_000, 48_000), 0f)
    }
}
