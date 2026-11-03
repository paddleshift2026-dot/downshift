package com.downshift.app.core.pack

import com.downshift.app.core.build.EngineId
import com.downshift.app.core.engine.Engines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProceduralPackTest {
    @Test
    fun aProceduralPackHasNoClipsAndSaysSo() {
        val spec = Engines.spec(EngineId.V8)
        val pack = ProceduralPack.build(spec, 48_000)
        assertTrue(pack.procedural)
        assertTrue(pack.clips.isEmpty())
        assertEquals(48_000, pack.sampleRate)
        assertEquals(ProceduralPack.SOURCE, pack.source)
    }

    @Test
    fun itsVoicingStillSpansIdleToNearRedline() {
        val spec = Engines.spec(EngineId.I4)
        val v = ProceduralPack.build(spec).voicing
        assertEquals(spec.idleRpm, v.idleRpm, 1e-9)
        assertTrue(v.onRpms.last() > 0.9 * spec.redlineRpm - 0.1 * spec.idleRpm)
    }
}
