package com.downshift.app.core.synth

import com.downshift.app.core.build.EngineId
import com.downshift.app.core.engine.Engines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FiringPlanTest {
    private fun intervals(angles: List<Double>) = angles.indices.map { i -> ((angles[(i + 1) % angles.size] - angles[i]) + 720.0) % 720.0 }

    @Test
    fun anInlineFourFiresEvery180DegreesInItsOrderOnOneBank() {
        val plan = FiringPlans.of(Engines.spec(EngineId.I4))
        assertEquals(listOf(0.0, 180.0, 360.0, 540.0), plan.map { it.angleDeg })
        assertEquals(listOf(1, 3, 4, 2), plan.map { it.cylinder })
        assertEquals(setOf(0), plan.map { it.bank }.toSet())
    }

    @Test
    fun aCrossPlaneV8FiresEvenlyButEachBankUnevenly() {
        val plan = FiringPlans.of(Engines.spec(EngineId.V8))
        assertEquals(setOf(90.0), intervals(plan.map { it.angleDeg }).toSet())
        val left = plan.filter { it.bank == 0 }.map { it.angleDeg }
        assertEquals(4, left.size)
        assertNotEquals(1, intervals(left).toSet().size) // the uneven gaps are the burble
    }

    @Test
    fun aV12AlternatesBanksAndEachBankIsEven() {
        val plan = FiringPlans.of(Engines.spec(EngineId.V12))
        assertEquals(plan.indices.map { it % 2 }, plan.map { it.bank })
        listOf(0, 1).forEach { b -> assertEquals(setOf(120.0), intervals(plan.filter { it.bank == b }.map { it.angleDeg }).toSet()) }
    }
}
