package com.downshift.app.core.input

import org.junit.Assert.assertEquals
import org.junit.Test

class InputArbiterTest {
    @Test
    fun aFreshConsolePedalWins() {
        val a = InputArbiter(staleMs = 500)
        a.console(0.7, 0.3, atMs = 1_000)
        a.manual(0.2)
        val p = a.pick(nowMs = 1_200, estimated = 0.4)
        assertEquals(ThrottleSource.CONSOLE, p.source)
        assertEquals(0.7, p.throttle, 1e-9)
        assertEquals(0.3, p.brake, 1e-9)
    }

    @Test
    fun aStaleConsoleFallsBackToTheManualValue() {
        val a = InputArbiter(staleMs = 500)
        a.console(0.7, 0.3, atMs = 1_000)
        a.manual(0.2)
        val p = a.pick(nowMs = 1_700, estimated = 0.4)
        assertEquals(ThrottleSource.MANUAL, p.source)
        assertEquals(0.2, p.throttle, 1e-9)
    }

    @Test
    fun withNeitherTheEstimateDrivesAndTheCarBrakeCounts() {
        val a = InputArbiter()
        a.vehicleBrake(true)
        val p = a.pick(nowMs = 1_000, estimated = 0.4)
        assertEquals(ThrottleSource.ESTIMATE, p.source)
        assertEquals(0.4, p.throttle, 1e-9)
        assertEquals(1.0, p.brake, 1e-9)
    }

    @Test
    fun clearingTheManualValueReturnsToTheEstimate() {
        val a = InputArbiter()
        a.manual(0.9)
        a.manual(null)
        assertEquals(ThrottleSource.ESTIMATE, a.pick(1_000, 0.1).source)
    }
}
