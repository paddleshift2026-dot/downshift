package com.downshift.app.core.dsp

import com.downshift.app.core.build.ExhaustPart
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ExhaustChainTest {
    private val fs = 44_100.0

    @Test
    fun trackIsTheBassiestAndStockTheDullest() {
        val stock = ExhaustChain(fs, ExhaustPart.STOCK)
        val sport = ExhaustChain(fs, ExhaustPart.SPORT)
        val track = ExhaustChain(fs, ExhaustPart.TRACK)
        assertTrue(track.gainDb(100.0) > sport.gainDb(100.0))
        assertTrue(sport.gainDb(100.0) > stock.gainDb(100.0))
        assertTrue(stock.gainDb(8000.0) < sport.gainDb(8000.0))
        assertTrue(track.drive > stock.drive)
    }

    @Test
    fun processingStaysFiniteAndBounded() {
        val c = ExhaustChain(fs, ExhaustPart.TRACK)
        val buf = FloatArray(4096) { sin(2 * PI * 90.0 * it / fs).toFloat() }
        c.process(buf, buf.size)
        assertTrue(buf.all { it.isFinite() && it in -1f..1f })
    }
}
