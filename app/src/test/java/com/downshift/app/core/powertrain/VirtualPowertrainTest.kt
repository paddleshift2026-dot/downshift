package com.downshift.app.core.powertrain

import com.downshift.app.core.build.EngineId
import com.downshift.app.core.build.TransmissionPart
import com.downshift.app.core.engine.Engines
import com.downshift.app.core.model.DriveInputs
import com.downshift.app.core.model.GearState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class VirtualPowertrainTest {
    private val spec = Engines.spec(EngineId.I4)
    private val dt = 0.01

    private fun powertrain(part: TransmissionPart = TransmissionPart.AT) = VirtualPowertrain(spec, Transmissions.of(part), part)

    private fun VirtualPowertrain.run(seconds: Double, inputs: DriveInputs, each: (PowertrainState, List<PowertrainEvent>) -> Unit = { _, _ -> }) {
        repeat((seconds / dt).roundToInt()) {
            val events = step(inputs, dt)
            each(state, events)
        }
    }

    private fun parked(throttle: Double = 0.0) = DriveInputs(throttle, 0.0, 0.0, GearState.PARK)
    private fun drive(speed: Double, throttle: Double) = DriveInputs(throttle, 0.0, speed, GearState.DRIVE)
    private fun started(part: TransmissionPart = TransmissionPart.AT) = powertrain(part).apply { start(); run(2.5, parked()) }

    @Test
    fun engineIsSilentUntilStarted() {
        val p = powertrain()
        p.run(1.0, parked(1.0))
        assertFalse(p.state.running)
        assertEquals(0.0, p.state.rpm, 1e-9)
    }

    @Test
    fun startupFlaresAboveIdleThenSettles() {
        val p = powertrain()
        p.start()
        var peak = 0.0
        val events = mutableListOf<PowertrainEvent>()
        p.run(0.8, parked()) { s, e -> peak = maxOf(peak, s.rpm); events += e }
        assertTrue(PowertrainEvent.STARTUP in events)
        assertTrue("peak $peak", peak > 1.4 * spec.idleRpm)
        p.run(2.0, parked())
        assertEquals(spec.idleRpm, p.state.rpm, 60.0)
        assertFalse(p.state.starting)
    }

    @Test
    fun parkedThrottleRevsWithInertiaAndHitsTheLimiter() {
        val p = started()
        p.run(0.15, parked(1.0))
        assertTrue("inertia ${p.state.rpm}", p.state.rpm < 0.8 * spec.redlineRpm && p.state.rpm > spec.idleRpm + 500)
        var limiter = false
        var max = 0.0
        val events = mutableListOf<PowertrainEvent>()
        p.run(1.5, parked(1.0)) { s, e -> limiter = limiter || s.atLimiter; max = maxOf(max, s.rpm); events += e }
        assertTrue(limiter)
        assertTrue(PowertrainEvent.LIMITER in events)
        assertTrue("max $max", max <= spec.limiterRpm + 1.0)
    }

    @Test
    fun releasingFallsSlowerThanRevvingRises() {
        val p = started()
        val mid = (spec.idleRpm + spec.redlineRpm) / 2
        var up = 0.0
        while (p.state.rpm < mid) { p.step(parked(1.0), dt); up += dt }
        p.run(1.5, parked(1.0))
        var down = 0.0
        while (p.state.rpm > mid) { p.step(parked(0.0), dt); down += dt }
        assertTrue("up $up down $down", down > up * 1.5)
    }

    @Test
    fun releasingARevIsOverrun() {
        val p = started()
        p.run(1.0, parked(1.0))
        val events = mutableListOf<PowertrainEvent>()
        p.run(0.1, parked(0.0)) { _, e -> events += e }
        assertTrue(p.state.overrun)
        assertEquals(1, events.count { it == PowertrainEvent.OVERRUN_START })
        p.run(3.0, parked(0.0))
        assertFalse(p.state.overrun)
    }

    @Test
    fun launchSlipLetsTheRpmRiseBeforeTheCarMoves() {
        val p = started()
        p.run(1.0, drive(0.5, 1.0))
        assertTrue("rpm ${p.state.rpm}", p.state.rpm > 0.4 * spec.redlineRpm)
        assertEquals(1, p.state.gear)
    }

    @Test
    fun cruisingRpmMatchesTheGearAndWheelSpeed() {
        val p = started()
        p.run(4.0, drive(20.0, 0.3))
        val gears = Transmissions.of(TransmissionPart.AT)
        val expected = coupledRpm(20.0, gears.ratios[p.state.gear - 1], gears.finalDrive)
        assertEquals(expected, p.state.rpm, expected * 0.03)
        assertTrue(expected < ShiftSchedule.upshiftRpm(0.3, spec.redlineRpm))
        assertTrue(expected > ShiftSchedule.downshiftRpm(0.3, spec.redlineRpm))
    }

    @Test
    fun acceleratingThroughTheGearsUpshiftsInOrder() {
        val p = started()
        var speed = 0.0
        var lastGear = 0
        var ups = 0
        var monotonic = true
        repeat(2000) {
            speed = minOf(35.0, speed + 1.75 * dt)
            ups += p.step(drive(speed, 0.6), dt).count { it == PowertrainEvent.SHIFT_UP }
            if (p.state.gear < lastGear) monotonic = false
            lastGear = p.state.gear
        }
        assertTrue("upshifts $ups", ups >= 4)
        assertTrue(monotonic)
    }

    @Test
    fun slowingDownDownshiftsAndOnlyTheManualBlips() {
        fun measure(part: TransmissionPart): Pair<Int, Double> {
            val p = started(part)
            val gears = Transmissions.of(part)
            p.run(5.0, drive(30.0, 0.3))
            var downs = 0
            var overshoot = 0.0
            var speed = 30.0
            repeat(1000) {
                speed = maxOf(5.0, speed - 2.5 * dt)
                downs += p.step(drive(speed, 0.0), dt).count { it == PowertrainEvent.SHIFT_DOWN }
                if (p.state.shifting && p.state.gear >= 1) {
                    overshoot = maxOf(overshoot, p.state.rpm - coupledRpm(speed, gears.ratios[p.state.gear - 1], gears.finalDrive))
                }
            }
            return downs to overshoot
        }
        val (mtDowns, mtBlip) = measure(TransmissionPart.MT_BLIP)
        val (atDowns, atBlip) = measure(TransmissionPart.AT)
        assertTrue("mt downshifts $mtDowns", mtDowns >= 2)
        assertTrue("at downshifts $atDowns", atDowns >= 2)
        assertTrue("mt blip $mtBlip", mtBlip >= 150.0)
        assertTrue("at overshoot $atBlip", atBlip < 150.0)
    }

    @Test
    fun liftingAtSpeedIsOverrunWithNoLoad() {
        val p = started()
        p.run(3.0, drive(25.0, 0.5))
        val events = mutableListOf<PowertrainEvent>()
        p.run(0.2, drive(25.0, 0.0)) { _, e -> events += e }
        assertTrue(p.state.overrun)
        assertTrue(p.state.load < 0.05)
        assertEquals(1, events.count { it == PowertrainEvent.OVERRUN_START })
    }

    @Test
    fun dctUpshiftsCutTheLoadBriefly() {
        val p = started(TransmissionPart.DCT)
        var speed = 0.0
        var minLoad = 1.0
        repeat(1000) {
            speed = minOf(30.0, speed + 3.0 * dt)
            p.step(drive(speed, 0.8), dt)
            if (p.state.shifting) minLoad = minOf(minLoad, p.state.load)
        }
        assertTrue("min load while shifting $minLoad", minLoad < 0.05)
    }

    @Test
    fun stoppingEmitsShutdownAndTheRpmDiesAway() {
        val p = started()
        p.stop()
        assertTrue(PowertrainEvent.SHUTDOWN in p.step(parked(), dt))
        p.run(2.0, parked())
        assertFalse(p.state.running)
        assertTrue(p.state.rpm < 1.0)
    }

    @Test
    fun theStarterCranksBeforeAnyCylinderFires() {
        val p = powertrain()
        p.start()
        p.step(parked(), dt)
        assertTrue(p.state.cranking)
        p.run(0.3, parked())
        assertFalse(p.state.cranking)
    }

    @Test
    fun theLimiterCutsTheFuelInBursts() {
        val p = started()
        var cutSteps = 0
        var steps = 0
        p.run(2.0, parked(1.0)) { s, _ -> if (s.fuelCut) cutSteps++; steps++ }
        assertTrue("cut $cutSteps of $steps steps", cutSteps in 1 until steps / 2)
        p.run(1.0, parked(0.0))
        assertFalse(p.state.fuelCut)
    }
}
