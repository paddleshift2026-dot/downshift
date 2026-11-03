package com.downshift.app.core.garage

import com.downshift.app.core.build.EngineId
import com.downshift.app.core.build.IntakePart
import com.downshift.app.core.build.TransmissionPart
import com.downshift.app.core.engine.Engines
import com.downshift.app.core.model.GearState
import com.downshift.app.core.powertrain.PowertrainEvent
import com.downshift.app.core.powertrain.Transmissions
import com.downshift.app.core.powertrain.VirtualPowertrain
import com.downshift.app.core.sound.InductionModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditionTest {
    private val dt = 0.01
    private val spec = Engines.spec(EngineId.I4)

    private class Run(val maxRpm: Double, val endRpm: Double, val ups: Int, val downs: Int, val overrunSec: Double, val maxBoost: Double, val blowOffs: Int, val endSpeed: Double, val endGear: GearState)

    private fun play(category: Category, trans: TransmissionPart = TransmissionPart.AT, intake: IntakePart = IntakePart.TURBO2): Run {
        val p = VirtualPowertrain(spec, Transmissions.of(trans), trans).apply { start() }
        val a = Audition(category, cold = true)
        val turbo = InductionModel(intake)
        var maxRpm = 0.0; var ups = 0; var downs = 0; var overrun = 0.0; var maxBoost = 0.0; var blowOffs = 0
        var t = 0.0
        var last = a.inputs(0.0)
        while (t <= a.durationSec) {
            last = a.inputs(t)
            val ev = p.step(last, dt)
            val s = p.state
            val ind = turbo.step(s.rpm, spec.redlineRpm, s.load, dt)
            if (t > 2.0) maxRpm = maxOf(maxRpm, s.rpm) // after the cold start
            ups += ev.count { it == PowertrainEvent.SHIFT_UP }
            downs += ev.count { it == PowertrainEvent.SHIFT_DOWN }
            if (s.overrun && s.rpm > 0.3 * spec.redlineRpm) overrun += dt
            maxBoost = maxOf(maxBoost, ind.state.boost)
            if (ind.blowOff) blowOffs++
            t += dt
        }
        repeat(150) { p.step(last, dt) }
        return Run(maxRpm, p.state.rpm, ups, downs, overrun, maxBoost, blowOffs, last.speedMps, last.gear)
    }

    @Test
    fun theEngineAuditionRevsAndSettles() {
        val r = play(Category.ENGINE)
        assertTrue("max ${r.maxRpm}", r.maxRpm >= 0.6 * spec.redlineRpm)
        assertTrue("end ${r.endRpm}", r.endRpm <= 1.3 * spec.idleRpm)
    }

    @Test
    fun theIntakeAuditionSpoolsUpThenBlowsOffOnce() {
        val r = play(Category.INTAKE)
        assertTrue("boost ${r.maxBoost}", r.maxBoost >= 0.4 * InductionModel.maxBoost(IntakePart.TURBO2))
        assertEquals(1, r.blowOffs)
    }

    @Test
    fun theExhaustAuditionCoastsInGear() {
        val r = play(Category.EXHAUST)
        assertTrue("overrun ${r.overrunSec}", r.overrunSec >= 0.5)
    }

    @Test
    fun theTransmissionAuditionShiftsUpAndDown() {
        val r = play(Category.TRANSMISSION, TransmissionPart.MT_BLIP)
        assertTrue("ups ${r.ups}", r.ups >= 2)
        assertTrue("downs ${r.downs}", r.downs >= 1)
    }

    @Test
    fun everyAuditionIsShortAndEndsParkedAtRest() {
        Category.entries.forEach { c ->
            val a = Audition(c, cold = false)
            assertTrue("$c ${a.durationSec}", a.durationSec <= 8.0)
            val r = play(c)
            assertEquals("$c speed", 0.0, r.endSpeed, 0.5)
            assertEquals("$c gear", GearState.PARK, r.endGear)
        }
    }
}
