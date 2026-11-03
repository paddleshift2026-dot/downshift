package com.downshift.app.core.sound

import com.downshift.app.core.build.Build
import com.downshift.app.core.build.TransmissionPart
import com.downshift.app.core.model.FuelKind
import com.downshift.app.core.powertrain.PowertrainEvent
import com.downshift.app.core.powertrain.PowertrainState
import com.downshift.app.core.synth.PulseDrive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundDirectorTest {
    private val voicing = Voicing(
        idleClip = "idle", idleRpm = 1000.0,
        onClips = listOf("low_on", "med_on", "high_on"), onRpms = listOf(2000.0, 4000.0, 6000.0),
        offClips = listOf("low_off", "med_off", "high_off"), offRpms = listOf(2000.0, 4000.0, 6000.0),
        startup = "startup", shutdown = "engine_off", limiter = "maxRPM",
    )
    private val director = SoundDirector(voicing)

    private fun state(rpm: Double, load: Double, running: Boolean = true, atLimiter: Boolean = false) =
        PowertrainState(running, rpm, load, gear = 0, shifting = false, overrun = false, atLimiter = atLimiter, starting = false)

    private fun plan(s: PowertrainState, events: List<PowertrainEvent> = emptyList(), build: Build = Build(), fuel: FuelKind = FuelKind.EV) =
        director.plan(s, events, InductionStep.NONE, emptyList(), build, fuel)

    @Test
    fun aStoppedEngineIsSilent() {
        val p = plan(state(0.0, 0.0, running = false))
        assertTrue(p.loops.isEmpty())
        assertEquals(0.0, p.master, 1e-9)
    }

    @Test
    fun idleIsTheIdleLoopAtItsNaturalPitch() {
        val p = plan(state(1000.0, 0.0))
        val idle = p.loops.single { it.clip == "idle" }
        assertEquals(1.0, idle.rate, 1e-9)
        assertEquals(1.0, idle.gain, 1e-6)
        assertTrue(p.loops.filter { it.clip != "idle" }.all { it.gain < 1e-6 })
    }

    @Test
    fun fullLoadBlendsOnlyTheOnLoops() {
        val p = plan(state(3000.0, 1.0))
        assertEquals(setOf("low_on", "med_on"), p.loops.filter { it.gain > 1e-6 }.map { it.clip }.toSet())
    }

    @Test
    fun pitchFollowsRpmAgainstEachLoopsOwnRpm() {
        val p = plan(state(3000.0, 1.0))
        assertEquals(1.5, p.loops.first { it.clip == "low_on" }.rate, 1e-9)
        assertEquals(0.75, p.loops.first { it.clip == "med_on" }.rate, 1e-9)
    }

    @Test
    fun halfLoadSplitsPowerBetweenOnAndOff() {
        val p = plan(state(4000.0, 0.5))
        val on = p.loops.first { it.clip == "med_on" }.gain
        val off = p.loops.first { it.clip == "med_off" }.gain
        assertEquals(on, off, 1e-9)
        assertEquals(1.0, on * on + off * off, 1e-9)
    }

    @Test
    fun startupAndShutdownPlayTheirOneShots() {
        assertEquals(listOf("startup"), plan(state(300.0, 0.0), listOf(PowertrainEvent.STARTUP)).shots.map { it.clip })
        val off = plan(state(500.0, 0.0, running = false), listOf(PowertrainEvent.SHUTDOWN))
        assertEquals(listOf("engine_off"), off.shots.map { it.clip })
        assertTrue(off.master > 0.0)
    }

    @Test
    fun theLimiterAddsTheLimiterLoop() {
        assertTrue(plan(state(6000.0, 1.0, atLimiter = true)).loops.any { it.clip == "maxRPM" && it.gain > 0.0 })
    }

    @Test
    fun dctUpshiftsCrackleButAutomaticOnesDoNot() {
        val dct = plan(state(5000.0, 0.8), listOf(PowertrainEvent.SHIFT_UP), Build(transmission = TransmissionPart.DCT))
        val at = plan(state(5000.0, 0.8), listOf(PowertrainEvent.SHIFT_UP), Build(transmission = TransmissionPart.AT))
        assertTrue(dct.pops.isNotEmpty())
        assertTrue(at.pops.isEmpty())
    }

    @Test
    fun loudnessRisesWithLoadAndRpm() {
        assertTrue(plan(state(5000.0, 1.0)).loudness > plan(state(1000.0, 0.0)).loudness)
    }

    @Test
    fun combustionCarsGetAQuieterBaselineAndVolumeScales() {
        assertTrue(plan(state(1000.0, 0.0), fuel = FuelKind.ICE).master < plan(state(1000.0, 0.0), fuel = FuelKind.EV).master)
        assertEquals(0.5 * plan(state(1000.0, 0.0), build = Build(volume = 1.0)).master, plan(state(1000.0, 0.0), build = Build(volume = 0.5)).master, 1e-9)
    }

    private val procedural = SoundDirector(voicing, procedural = true)

    private fun pulsePlan(s: PowertrainState, events: List<PowertrainEvent> = emptyList()) =
        procedural.plan(s, events, InductionStep.NONE, emptyList(), Build(), FuelKind.EV)

    @Test
    fun aProceduralEngineDrivesThePulseVoiceInsteadOfLoops() {
        val p = pulsePlan(state(3000.0, 0.8))
        assertTrue(p.loops.isEmpty())
        assertEquals(PulseDrive(3000.0, 0.8, combustion = true, cranking = false), p.pulse)
    }

    @Test
    fun crankingAndTheLimiterCutStopTheFiring() {
        val crank = pulsePlan(PowertrainState(true, 200.0, 0.0, 0, false, false, false, starting = true, cranking = true)).pulse!!
        assertFalse(crank.combustion)
        assertTrue(crank.cranking)
        assertFalse(pulsePlan(PowertrainState(true, 7000.0, 0.0, 0, false, false, true, false, fuelCut = true)).pulse!!.combustion)
    }

    @Test
    fun theStartupFlareFiresHarderThanIdle() {
        val flare = pulsePlan(PowertrainState(true, 1500.0, 0.0, 0, false, false, false, starting = true)).pulse!!
        assertTrue("flare load ${flare.load}", flare.load > 0.3)
    }

    @Test
    fun aStoppedProceduralEngineIsHeardSpinningDown() {
        val spinning = pulsePlan(state(500.0, 0.0, running = false))
        assertTrue(spinning.master > 0.0)
        assertFalse(spinning.pulse!!.combustion)
        assertEquals(0.0, pulsePlan(state(0.0, 0.0, running = false)).master, 1e-9)
    }

    @Test
    fun aProceduralStartNeedsNoOneShot() = assertTrue(pulsePlan(state(300.0, 0.0), listOf(PowertrainEvent.STARTUP)).shots.isEmpty())
}
