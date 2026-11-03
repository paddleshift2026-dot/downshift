package com.downshift.app.core.runtime

import com.downshift.app.core.build.Build
import com.downshift.app.core.build.EngineId
import com.downshift.app.core.engine.Engines
import com.downshift.app.core.model.DriveInputs
import com.downshift.app.core.model.FuelKind
import com.downshift.app.core.model.GearState
import com.downshift.app.core.pack.ProceduralPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EngineRuntimeQuietTest {
    private val fs = 48_000
    private val block = 720
    private val park = DriveInputs(0.0, 0.0, 0.0, GearState.PARK)

    private fun runtime() = EngineRuntime(ProceduralPack.build(Engines.spec(EngineId.I4), fs), Build(), FuelKind.EV, fs)

    /** Renders [sec] seconds parked and returns the loudest sample. */
    private fun EngineRuntime.run(sec: Double): Float {
        val buf = FloatArray(block)
        var peak = 0f
        repeat((sec * fs / block).toInt()) {
            renderBlock(park, buf, block)
            for (v in buf) peak = maxOf(peak, abs(v))
        }
        return peak
    }

    @Test
    fun anEngineThatWasNeverStartedIsQuiet() {
        val rt = runtime()
        assertEquals(0f, rt.run(1.0), 0f)
        assertTrue(rt.quiet)
    }

    @Test
    fun aStoppedEngineGoesQuietOnceItHasSpunDownAndWakesOnStart() {
        val rt = runtime()
        rt.start()
        assertTrue(rt.run(2.0) > 0.01f)
        assertFalse(rt.quiet)
        rt.stop()
        rt.run(0.3)
        assertFalse("still spinning down", rt.quiet)
        rt.run(2.0)
        assertTrue(rt.quiet)
        assertEquals(0f, rt.run(1.0), 0f)
        rt.start()
        assertTrue(rt.run(1.0) > 0.01f)
        assertFalse(rt.quiet)
    }
}
