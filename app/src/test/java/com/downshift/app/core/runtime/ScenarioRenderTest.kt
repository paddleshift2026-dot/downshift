package com.downshift.app.core.runtime

import com.downshift.app.core.build.Build
import com.downshift.app.core.build.EngineId
import com.downshift.app.core.build.ExhaustPart
import com.downshift.app.core.build.IntakePart
import com.downshift.app.core.build.TransmissionPart
import com.downshift.app.core.dsp.WavWriter
import com.downshift.app.core.engine.Engines
import com.downshift.app.core.model.DriveInputs
import com.downshift.app.core.model.FuelKind
import com.downshift.app.core.model.GearState
import com.downshift.app.core.pack.SynthPack
import com.downshift.app.core.powertrain.DemoVehicleModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

class ScenarioRenderTest {
    private val fs = 44_100
    private val block = 512

    /** Off, start, idle, rev to the limiter in P, release, pull through the gears, lift, brake, park, stop. */
    private fun render(engine: EngineId): Pair<FloatArray, List<Telemetry>> {
        val build = Build(engine, IntakePart.TURBO2, ExhaustPart.SPORT, TransmissionPart.DCT, pops = 0.8)
        val rt = EngineRuntime(SynthPack.build(Engines.spec(engine), fs), build, FuelKind.EV)
        val car = DemoVehicleModel()
        val out = ArrayList<Float>()
        val tele = mutableListOf<Telemetry>()
        val buf = FloatArray(block)
        fun run(seconds: Double, throttle: Double, brake: Double, gear: GearState) {
            repeat((seconds * fs / block).toInt()) {
                val speed = car.step(throttle, brake, gear, block.toDouble() / fs)
                rt.renderBlock(DriveInputs(throttle, brake, speed, gear), buf, block)
                buf.forEach { out.add(it) }
                tele += rt.telemetry
            }
        }
        run(0.5, 0.0, 0.0, GearState.PARK)
        rt.start(); run(2.5, 0.0, 0.0, GearState.PARK)
        run(1.5, 1.0, 0.0, GearState.PARK)
        run(1.5, 0.0, 0.0, GearState.PARK)
        run(9.0, 0.8, 0.0, GearState.DRIVE)
        run(3.0, 0.0, 0.0, GearState.DRIVE)
        run(4.0, 0.0, 0.7, GearState.DRIVE)
        run(1.0, 0.0, 0.0, GearState.PARK)
        rt.stop(); run(2.0, 0.0, 0.0, GearState.PARK)
        return out.toFloatArray() to tele
    }

    private fun rms(pcm: FloatArray, from: Double, to: Double): Double {
        val a = (from * fs).toInt()
        val b = (to * fs).toInt()
        return sqrt((a until b).sumOf { (pcm[it] * pcm[it]).toDouble() } / (b - a))
    }

    @Test
    fun everyEngineRendersAFiniteBoundedScenarioWithShiftsAndTheLimiter() {
        File("build/renders").mkdirs()
        EngineId.entries.forEach { id ->
            val (pcm, tele) = render(id)
            assertTrue("$id finite", pcm.all { it.isFinite() })
            assertTrue("$id bounded", pcm.all { it in -1f..1f })
            assertTrue("$id shifted", tele.maxOf { it.gear } >= 3)
            assertTrue("$id hit the limiter", tele.any { it.atLimiter })
            File("build/renders/${id.name.lowercase()}_scenario.wav").writeBytes(WavWriter.pcm16Mono(pcm, pcm.size, fs))
        }
    }

    @Test
    fun revvingIsLouderThanIdling() {
        val (pcm, _) = render(EngineId.V8)
        assertTrue(rms(pcm, 3.5, 4.4) > 1.5 * rms(pcm, 2.3, 3.0))
    }

    @Test
    fun beforeStartingThereIsSilence() {
        val (pcm, _) = render(EngineId.I4)
        assertEquals(0.0, rms(pcm, 0.0, 0.5), 1e-9)
    }
}
