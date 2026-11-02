package com.downshift.app.core.pack

import com.downshift.app.core.audio.TestTone
import com.downshift.app.core.build.EngineId
import com.downshift.app.core.engine.Engines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SynthPackTest {
    @Test
    fun providesEveryClipTheVoicingNeeds() {
        EngineId.entries.forEach { id ->
            val pack = SynthPack.build(Engines.spec(id))
            val needed = listOf("idle", "startup", "engine_off", "maxRPM") + (1..7).flatMap { listOf("on_$it", "off_$it") }
            assertTrue("$id", needed.all { it in pack.clips.keys })
            assertEquals(needed.toSet() - setOf("startup", "engine_off", "maxRPM"), (pack.voicing.onClips + pack.voicing.offClips + pack.voicing.idleClip).toSet())
        }
    }

    @Test
    fun loopsAreWholeFiringPeriodsSoTheyJoinCleanly() {
        val spec = Engines.spec(EngineId.I4)
        val pack = SynthPack.build(spec)
        val idle = pack.clips.getValue("idle")
        val period = 44_100 / TestTone.firingHz(spec.idleRpm, spec.cylinders)
        assertEquals(0.0, (idle.size / period) % 1.0, 0.02)
        assertTrue(abs(idle.last() - idle.first()) < 0.1f)
    }

    @Test
    fun theVoicingStartsAtTheEngineIdle() {
        val spec = Engines.spec(EngineId.V12)
        assertEquals(spec.idleRpm, SynthPack.build(spec).voicing.idleRpm, 1e-9)
        assertEquals("synth", SynthPack.build(spec).source)
    }
}
