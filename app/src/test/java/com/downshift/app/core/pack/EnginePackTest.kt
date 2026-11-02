package com.downshift.app.core.pack

import com.downshift.app.core.build.EngineId
import com.downshift.app.core.engine.Engines
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnginePackTest {
    private val json = """
        {"engine": "I4", "source": "test", "sampleRate": 44100, "clips": [
          {"name": "idle", "file": "idle.dsa", "rpm": 900},
          {"name": "on_1", "file": "a.dsa", "rpm": 1600}, {"name": "on_2", "file": "b.dsa"}, {"name": "on_3", "file": "c.dsa"}, {"name": "on_4", "file": "d.dsa"},
          {"name": "off_1", "file": "e.dsa"}, {"name": "off_2", "file": "f.dsa"}, {"name": "off_3", "file": "g.dsa"}, {"name": "off_4", "file": "h.dsa"},
          {"name": "startup", "file": "s.dsa"}, {"name": "engine_off", "file": "o.dsa"}, {"name": "maxRPM", "file": "m.dsa"}]}
    """.trimIndent()

    @Test
    fun parsesTheManifest() {
        val m = Json.decodeFromString(EnginePackManifest.serializer(), json)
        assertEquals("I4", m.engine)
        assertEquals(44_100, m.sampleRate)
        assertEquals(12, m.clips.size)
        assertTrue(PackRules.problems(m).isEmpty())
    }

    @Test
    fun manifestRpmsWinAndMissingOnesFollowTheDefaultLadder() {
        val m = Json.decodeFromString(EnginePackManifest.serializer(), json)
        val v = PackRules.voicing(m, Engines.spec(EngineId.I4))
        assertEquals(900.0, v.idleRpm, 1e-9)
        assertEquals(1600.0, v.onRpms[0], 1e-9)
        assertEquals(listOf("on_1", "on_2", "on_3", "on_4"), v.onClips)
        assertTrue(v.onRpms.zipWithNext().all { (a, b) -> b > a })
        assertEquals("startup", v.startup)
    }

    @Test
    fun missingClipsAreReported() {
        val m = EnginePackManifest("V8", "test", sampleRate = 44_100, clips = listOf(PackClip("on_1", "a.dsa")))
        val problems = PackRules.problems(m)
        assertTrue(problems.any { "idle" in it })
        assertTrue(problems.any { "on" in it })
        assertTrue(problems.any { "startup" in it })
    }
}
