package com.downshift.app.core.dsp

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class GainGlideTest {
    private fun GainGlide.run(target: Double, sec: Double, dt: Double = 0.015): Double {
        var v = value
        repeat((sec / dt).roundToInt()) { v = next(target, dt) }
        return v
    }

    @Test
    fun itDucksWithinAThirdOfASecond() = assertTrue(GainGlide(1.0).run(0.25, 0.36) <= 0.33)

    @Test
    fun itComesBackSlowerThanItWent() {
        val g = GainGlide(0.25)
        assertTrue(g.run(1.0, 0.3) < 0.6)
        assertTrue(g.run(1.0, 1.7) > 0.95) // two seconds in all
    }
}
