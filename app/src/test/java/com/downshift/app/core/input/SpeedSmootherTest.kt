package com.downshift.app.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpeedSmootherTest {
    private val block = 720 / 48_000.0

    @Test
    fun theFirstSamplePassesStraightThrough() = assertEquals(22.0, SpeedSmoother().step(22.0, block), 1e-9)

    @Test
    fun aSteadySpeedIsReachedExactly() {
        val s = SpeedSmoother()
        s.step(10.0, block)
        var v = 0.0
        repeat(200) { v = s.step(20.0, block) }
        assertEquals(20.0, v, 1e-6)
    }

    @Test
    fun aTenHertzStaircaseComesOutAsARamp() {
        // 1 km/h steps every 100 ms, as the console and AAOS deliver an accelerating car
        val s = SpeedSmoother()
        var t = 0.0
        var prev = s.step(0.0, block)
        var maxStep = 0.0
        var input = 0.0
        while (t < 3.0) {
            input = (t / 0.1).toInt() * (1 / 3.6)
            val v = s.step(input, block)
            maxStep = maxOf(maxStep, abs(v - prev))
            prev = v
            t += block
        }
        assertTrue("largest change per block $maxStep m/s", maxStep < 0.4 * (1 / 3.6))
        // it trails the input by about tau × slope plus one step, never by seconds
        assertEquals(input, prev, 0.6)
    }
}
