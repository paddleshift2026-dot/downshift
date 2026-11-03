package com.downshift.app.core.runtime

import com.downshift.app.core.build.Build
import com.downshift.app.core.build.EngineId
import com.downshift.app.core.engine.Engines
import com.downshift.app.core.model.DriveInputs
import com.downshift.app.core.model.FuelKind
import com.downshift.app.core.model.GearState
import com.downshift.app.core.pack.ProceduralPack
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class EngineRuntimeGainTest {
    private val fs = 48_000
    private val block = 720
    private val park = DriveInputs(0.0, 0.0, 0.0, GearState.PARK)

    /** Renders [sec] seconds parked and returns the RMS level. */
    private fun EngineRuntime.rms(sec: Double): Double {
        val buf = FloatArray(block)
        var sum = 0.0
        var n = 0
        repeat((sec * fs / block).toInt()) {
            renderBlock(park, buf, block)
            for (v in buf) { sum += v * v; n++ }
        }
        return sqrt(sum / n)
    }

    private fun idling() = EngineRuntime(ProceduralPack.build(Engines.spec(EngineId.I4), fs), Build(), FuelKind.EV, fs).apply {
        start()
        rms(3.0)
    }

    @Test
    fun theSituationGainScalesTheEngine() {
        val full = idling().rms(1.0)
        val rt = idling()
        rt.setSituationGain(0.25)
        rt.rms(1.0) // let the glide settle
        val ducked = rt.rms(1.0)
        assertTrue("ratio ${ducked / full}", ducked / full in 0.15..0.35)
    }

    @Test
    fun zeroIsSilentAndOneComesBack() {
        val rt = idling()
        rt.setSituationGain(0.0)
        rt.rms(2.0) // the glide is down to about 1e-6 after two seconds
        assertTrue(rt.rms(0.5) < 1e-4)
        rt.setSituationGain(1.0)
        rt.rms(3.0)
        assertTrue(rt.rms(1.0) > 1e-3)
    }
}
