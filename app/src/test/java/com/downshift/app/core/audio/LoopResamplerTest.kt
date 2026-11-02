package com.downshift.app.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LoopResamplerTest {
    private val ramp = floatArrayOf(0f, 1f, 2f, 3f)

    @Test
    fun rateOneCopiesTheLoopAndWraps() {
        val out = FloatArray(6)
        LoopResampler(ramp).render(out)
        assertArrayEquals(floatArrayOf(0f, 1f, 2f, 3f, 0f, 1f), out, 1e-6f)
    }

    @Test
    fun rateTwoSkipsEveryOtherSample() {
        val out = FloatArray(4)
        LoopResampler(ramp).apply { rate = 2.0 }.render(out)
        assertArrayEquals(floatArrayOf(0f, 2f, 0f, 2f), out, 1e-6f)
    }

    @Test
    fun rateHalfInterpolatesBetweenSamples() {
        val out = FloatArray(4)
        LoopResampler(ramp).apply { rate = 0.5 }.render(out)
        assertArrayEquals(floatArrayOf(0f, 0.5f, 1f, 1.5f), out, 1e-6f)
    }

    @Test
    fun lastSampleInterpolatesTowardTheFirst() {
        val out = FloatArray(8)
        LoopResampler(ramp).apply { rate = 0.5 }.render(out)
        assertEquals(1.5f, out[7], 1e-6f)
    }

    @Test
    fun positionCarriesOverBetweenCalls() {
        val r = LoopResampler(ramp)
        val a = FloatArray(3)
        val b = FloatArray(3)
        r.render(a)
        r.render(b)
        assertArrayEquals(floatArrayOf(3f, 0f, 1f), b, 1e-6f)
    }

    @Test
    fun rateIsClamped() {
        val r = LoopResampler(ramp)
        r.rate = 10.0
        assertEquals(LoopResampler.MAX_RATE, r.rate, 1e-9)
        r.rate = 0.0
        assertEquals(LoopResampler.MIN_RATE, r.rate, 1e-9)
    }
}
