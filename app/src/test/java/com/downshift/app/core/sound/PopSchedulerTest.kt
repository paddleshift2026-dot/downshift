package com.downshift.app.core.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PopSchedulerTest {
    private fun count(p: PopScheduler, overrun: Boolean, intensity: Double, seconds: Double = 2.0): Int {
        var n = 0
        repeat((seconds / 0.01).toInt()) { n += p.step(overrun, 0.8, intensity, 0.01).size }
        return n
    }

    @Test
    fun noPopsOutsideOverrun() = assertEquals(0, count(PopScheduler(1), overrun = false, intensity = 1.0))

    @Test
    fun noPopsAtZeroIntensity() = assertEquals(0, count(PopScheduler(1), overrun = true, intensity = 0.0))

    @Test
    fun fullIntensityOverrunCrackles() {
        val n = count(PopScheduler(1), overrun = true, intensity = 1.0)
        assertTrue("pops in 2 s: $n", n in 10..60)
    }

    @Test
    fun moreIntensityMeansMorePops() {
        assertTrue(count(PopScheduler(3), true, 0.3) < count(PopScheduler(3), true, 1.0))
    }

    @Test
    fun theSameSeedCracklesTheSameWay() {
        val a = PopScheduler(7)
        val b = PopScheduler(7)
        repeat(300) { assertEquals(a.step(true, 0.8, 1.0, 0.01), b.step(true, 0.8, 1.0, 0.01)) }
    }

    @Test
    fun popsStayInRange() {
        val p = PopScheduler(11)
        repeat(1000) {
            p.step(true, 1.0, 1.0, 0.01).forEach { pop ->
                assertTrue(pop.gain in 0.0..1.0)
                assertTrue(pop.durationMs in 2.0..15.0)
                assertTrue(pop.cutoffHz in 1500.0..6000.0)
            }
        }
    }
}
