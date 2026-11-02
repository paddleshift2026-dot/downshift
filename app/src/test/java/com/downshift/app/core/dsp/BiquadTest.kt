package com.downshift.app.core.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin

class BiquadTest {
    private val fs = 44_100.0

    @Test
    fun lowShelfLiftsTheBassByItsGain() {
        val f = Biquad.lowShelf(fs, 150.0, 6.0)
        assertEquals(6.0, f.gainDb(1.0, fs), 0.1)
        assertEquals(0.0, f.gainDb(fs / 2 * 0.999, fs), 0.1)
    }

    @Test
    fun highShelfCutsTheTrebleByItsGain() {
        val f = Biquad.highShelf(fs, 4000.0, -4.0)
        assertEquals(-4.0, f.gainDb(fs / 2 * 0.999, fs), 0.1)
        assertEquals(0.0, f.gainDb(1.0, fs), 0.1)
    }

    @Test
    fun peakingHitsItsGainAtTheCentre() = assertEquals(3.0, Biquad.peaking(fs, 400.0, 3.0, q = 1.0).gainDb(400.0, fs), 0.05)

    @Test
    fun zeroGainPeakingIsTransparent() {
        val f = Biquad.peaking(fs, 1000.0, 0.0, q = 1.0)
        listOf(50.0, 1000.0, 15_000.0).forEach { assertEquals(0.0, f.gainDb(it, fs), 1e-6) }
    }

    @Test
    fun lowPassPassesDcAndKillsHighs() {
        val f = Biquad.lowPass(fs, 1000.0)
        assertEquals(0.0, f.gainDb(1.0, fs), 0.01)
        assertTrue(f.gainDb(10_000.0, fs) < -30.0)
    }

    @Test
    fun impulseResponseDiesAway() {
        val f = Biquad.peaking(fs, 400.0, 6.0, q = 2.0)
        f.process(1f)
        var last = 1f
        repeat(20_000) { last = f.process(0f) }
        assertEquals(0f, last, 1e-6f)
    }

    @Test
    fun processingMatchesTheTransferFunction() {
        val f = Biquad.lowShelf(fs, 150.0, 6.0)
        val n = 44_100
        var inSq = 0.0
        var outSq = 0.0
        for (i in 0 until n) {
            val x = sin(2 * PI * 60.0 * i / fs).toFloat()
            val y = f.process(x)
            if (i > n / 2) { inSq += x * x; outSq += y * y }
        }
        assertEquals(f.gainDb(60.0, fs), 10 * log10(outSq / inSq), 0.1)
    }
}
