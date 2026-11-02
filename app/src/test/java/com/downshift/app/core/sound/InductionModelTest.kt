package com.downshift.app.core.sound

import com.downshift.app.core.build.IntakePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class InductionModelTest {
    private val redline = 6800.0

    private fun InductionModel.hold(rpm: Double, load: Double, seconds: Double): InductionState {
        var s = InductionState.ZERO
        repeat((seconds / 0.01).roundToInt()) { s = step(rpm, redline, load, 0.01).state }
        return s
    }

    @Test
    fun naturallyAspiratedAddsNothing() {
        val s = InductionModel(IntakePart.NA).hold(5000.0, 1.0, 2.0)
        assertEquals(0.0, s.boost, 1e-9)
        assertEquals(0.0, s.whistleGain, 1e-9)
        assertEquals(0.0, s.whineGain, 1e-9)
    }

    @Test
    fun turboBoostLagsBehindThePedal() {
        val m = InductionModel(IntakePart.TURBO2)
        val early = m.hold(5000.0, 1.0, 0.2).boost
        val late = m.hold(5000.0, 1.0, 4.0).boost
        assertTrue("early $early late $late", early < 0.5 * late)
        assertTrue(late > 0.9 * InductionModel.maxBoost(IntakePart.TURBO2))
    }

    @Test
    fun biggerTurbosSpoolSlowerButBoostMore() {
        val t1 = InductionModel(IntakePart.TURBO1).hold(5000.0, 1.0, 0.5).boost / InductionModel.maxBoost(IntakePart.TURBO1)
        val t3 = InductionModel(IntakePart.TURBO3).hold(5000.0, 1.0, 0.5).boost / InductionModel.maxBoost(IntakePart.TURBO3)
        assertTrue(t1 > t3)
        assertTrue(InductionModel.maxBoost(IntakePart.TURBO3) > InductionModel.maxBoost(IntakePart.TURBO1))
    }

    @Test
    fun lowRpmMakesLittleBoost() {
        val s = InductionModel(IntakePart.TURBO2).hold(1200.0, 1.0, 4.0)
        assertTrue(s.boost < 0.3 * InductionModel.maxBoost(IntakePart.TURBO2))
    }

    @Test
    fun blowOffFiresOnceWhenLiftingUnderBoost() {
        val m = InductionModel(IntakePart.TURBO2)
        m.hold(5000.0, 1.0, 3.0)
        assertTrue(m.step(5000.0, redline, 0.0, 0.01).blowOff)
        assertFalse(m.step(5000.0, redline, 0.0, 0.01).blowOff)
    }

    @Test
    fun noBlowOffWithoutBoost() {
        val m = InductionModel(IntakePart.TURBO2)
        m.hold(1000.0, 0.5, 3.0)
        assertFalse(m.step(1000.0, redline, 0.0, 0.01).blowOff)
    }

    @Test
    fun stageOneHasNoBlowOffValve() {
        val m = InductionModel(IntakePart.TURBO1)
        m.hold(5000.0, 1.0, 3.0)
        assertFalse(m.step(5000.0, redline, 0.0, 0.01).blowOff)
    }

    @Test
    fun superchargerWhineTracksRpmWithoutLag() {
        val m = InductionModel(IntakePart.SUPERCHARGER)
        val a = m.step(3000.0, redline, 0.5, 0.01).state
        val b = m.step(6000.0, redline, 0.5, 0.01).state
        assertEquals(2.0, b.whineHz / a.whineHz, 1e-6)
        assertTrue(a.whineGain > 0.0)
    }
}
