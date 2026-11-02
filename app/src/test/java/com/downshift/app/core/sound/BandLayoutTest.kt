package com.downshift.app.core.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class BandLayoutTest {
    private val rpms = listOf(1000.0, 2000.0, 3000.0, 4000.0)

    @Test
    fun atABandOnlyThatBandSounds() {
        val w = BandLayout.weights(rpms, 2000.0)
        assertEquals(1, w.low)
        assertEquals(1.0, w.lowGain, 1e-9)
        assertEquals(0.0, w.highGain, 1e-9)
    }

    @Test
    fun halfwayIsEqualPower() {
        val w = BandLayout.weights(rpms, 2500.0)
        assertEquals(1, w.low)
        assertEquals(2, w.high)
        assertEquals(sqrt(0.5), w.lowGain, 1e-9)
        assertEquals(sqrt(0.5), w.highGain, 1e-9)
    }

    @Test
    fun outsideTheLadderClampsToTheEnds() {
        val below = BandLayout.weights(rpms, 500.0)
        assertEquals(0, below.low)
        assertEquals(1.0, below.lowGain, 1e-9)
        val above = BandLayout.weights(rpms, 9000.0)
        assertEquals(3, above.low)
        assertEquals(1.0, above.lowGain, 1e-9)
    }

    @Test
    fun crossfadesAlwaysKeepPower() {
        (1000..4000 step 37).forEach { r ->
            val w = BandLayout.weights(rpms, r.toDouble())
            assertEquals("rpm $r", 1.0, w.lowGain * w.lowGain + w.highGain * w.highGain, 1e-9)
        }
    }

    @Test
    fun defaultLadderSpansIdleToJustUnderRedline() {
        val l = BandLayout.defaultRpms(880.0, 6800.0)
        assertEquals(8, l.size)
        assertEquals(880.0, l.first(), 1e-9)
        assertTrue(l.last() > 6000.0 && l.last() < 6800.0)
        assertTrue(l.zipWithNext().all { (a, b) -> b > a })
    }
}
