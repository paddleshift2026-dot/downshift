package com.downshift.app.core.engine

import com.downshift.app.core.build.EngineId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineSpecTest {
    @Test
    fun everyEngineHasOrderedRpmLimits() {
        EngineId.entries.forEach { id ->
            val s = Engines.spec(id)
            assertTrue("$id idle < redline", s.idleRpm < s.redlineRpm)
            assertTrue("$id redline < limiter", s.redlineRpm < s.limiterRpm)
            assertTrue("$id revs faster than it falls", s.riseTau < s.fallTau)
        }
    }

    @Test
    fun cylinderCountsMatchTheNames() {
        assertEquals(4, Engines.spec(EngineId.I4).cylinders)
        assertEquals(8, Engines.spec(EngineId.V8).cylinders)
        assertEquals(12, Engines.spec(EngineId.V12).cylinders)
    }

    @Test
    fun redlineOffsetIsClampedToFiveHundred() {
        val s = Engines.spec(EngineId.I4)
        assertEquals(s.redlineRpm + 500, s.withRedlineOffset(800).redlineRpm, 1e-9)
        assertEquals(s.redlineRpm - 500, s.withRedlineOffset(-900).redlineRpm, 1e-9)
        val raised = s.withRedlineOffset(500)
        assertTrue(raised.limiterRpm > raised.redlineRpm)
    }
}
