package com.downshift.app.core.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DspPartsTest {
    @Test
    fun softClipIsBoundedOddAndMonotonic() {
        assertEquals(1f, softClip(10f, 2f), 1e-6f)
        assertEquals(-softClip(0.4f, 1.5f), softClip(-0.4f, 1.5f), 1e-6f)
        val xs = (-100..100).map { it / 100f }
        assertTrue(xs.zipWithNext().all { (a, b) -> softClip(b, 1.6f) >= softClip(a, 1.6f) })
    }

    @Test
    fun tinyDriveIsAlmostIdentity() = assertEquals(0.3f, softClip(0.3f, 0.0001f), 1e-4f)

    @Test
    fun limiterIsTransparentBelowItsKneeAndBoundedAbove() {
        assertEquals(0.5f, limit(0.5f), 1e-6f)
        assertTrue(limit(5f) <= 1f)
        assertTrue(limit(-5f) >= -1f)
    }

    @Test
    fun gainRampMovesInEqualStepsAndLandsOnTarget() {
        val r = GainRamp(0f)
        r.rampTo(1f, 100)
        var prev = 0f
        var maxStep = 0f
        repeat(100) { val v = r.next(); maxStep = maxOf(maxStep, abs(v - prev)); prev = v }
        assertEquals(1f, prev, 1e-6f)
        assertTrue(maxStep <= 0.0101f)
        assertEquals(1f, r.next(), 1e-6f)
    }

    @Test
    fun sineOscillatorKeepsItsPhaseWhenTheFrequencyChanges() {
        val o = SineOsc(44_100.0)
        o.freqHz = 440.0
        var last = 0f
        repeat(1000) { last = o.next() }
        o.freqHz = 880.0
        assertTrue(abs(o.next() - last) < 0.2f)
    }

    @Test
    fun noiseIsSeededAndBounded() {
        val a = NoiseSource(5)
        val b = NoiseSource(5)
        repeat(1000) {
            val x = a.next()
            assertEquals(x, b.next(), 0f)
            assertTrue(x in -1f..1f)
        }
    }
}
