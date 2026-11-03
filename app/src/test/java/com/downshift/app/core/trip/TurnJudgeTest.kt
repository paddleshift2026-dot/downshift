package com.downshift.app.core.trip

import org.junit.Assert.assertEquals
import org.junit.Test

class TurnJudgeTest {
    private val left = TurnAhead(Side.LEFT, highway = false, remainM = 100.0)

    /** Drives [metres] at 10 m/s in 1 m steps, holding [signal] while the turn is [from] down to [until] metres ahead. */
    private fun TurnJudge.drive(turnAt: Double, metres: Int, signal: Signal, from: Double, until: Double = Double.NEGATIVE_INFINITY) {
        for (k in 1..metres) {
            val ahead = turnAt - k
            step(10.0, if (ahead <= from && ahead >= until) signal else Signal.OFF, 0.1)
        }
    }

    @Test
    fun signallingFromThirtyMetresScores() {
        val j = TurnJudge()
        j.announce(left)
        j.drive(100.0, 110, Signal.LEFT, from = 40.0)
        assertEquals(1, j.turns)
        assertEquals(1, j.signalled)
    }

    @Test
    fun lateWrongOrCancelledSignalsDoNotScore() {
        val late = TurnJudge().apply { announce(left); drive(100.0, 110, Signal.LEFT, from = 20.0) }
        val wrong = TurnJudge().apply { announce(left); drive(100.0, 110, Signal.RIGHT, from = 40.0) }
        val cancelled = TurnJudge().apply { announce(left); drive(100.0, 110, Signal.LEFT, from = 40.0, until = 10.0) }
        listOf(late, wrong, cancelled).forEach {
            assertEquals(1, it.turns)
            assertEquals(0, it.signalled)
        }
    }

    @Test
    fun highwayManoeuvresNeedAHundredMetres() {
        val exit = TurnAhead(Side.RIGHT, highway = true, remainM = 300.0)
        val early = TurnJudge().apply { announce(exit); drive(300.0, 310, Signal.RIGHT, from = 120.0) }
        val short = TurnJudge().apply { announce(exit); drive(300.0, 310, Signal.RIGHT, from = 80.0) }
        assertEquals(1, early.signalled)
        assertEquals(0, short.signalled)
    }

    @Test
    fun refreshesKeepTheTurnAndAReRouteReplacesIt() {
        val j = TurnJudge()
        j.announce(left)
        j.drive(100.0, 30, Signal.OFF, from = 0.0) // 70 m to go
        j.announce(left.copy(remainM = 72.0)) // the navi's own figure for the same turn
        j.drive(72.0, 80, Signal.LEFT, from = 40.0)
        assertEquals(1, j.turns)
        assertEquals(1, j.signalled)
        j.announce(TurnAhead(Side.RIGHT, highway = false, remainM = 200.0))
        j.drive(200.0, 20, Signal.OFF, from = 0.0)
        j.announce(left.copy(remainM = 80.0)) // re-routed: the right turn is gone
        j.drive(80.0, 90, Signal.LEFT, from = 50.0)
        assertEquals(2, j.turns)
        assertEquals(2, j.signalled)
    }

    @Test
    fun aRepeatOfTheTurnJustTakenIsIgnored() {
        val j = TurnJudge()
        j.announce(left)
        j.drive(100.0, 101, Signal.LEFT, from = 40.0)
        j.announce(left.copy(remainM = 3.0))
        j.drive(3.0, 30, Signal.OFF, from = 0.0)
        assertEquals(1, j.turns)
    }

    @Test
    fun turnTypesMapToASideAndALead() {
        assertEquals(Side.LEFT to false, TurnKinds.of("TURN_LEFT"))
        assertEquals(Side.LEFT to false, TurnKinds.of("TURN_UTURN"))
        assertEquals(Side.RIGHT to false, TurnKinds.of("TURN_SHARP_R"))
        assertEquals(Side.RIGHT to true, TurnKinds.of("TURN_EXIT_IC_R"))
        assertEquals(Side.RIGHT to true, TurnKinds.of("TURN_RESTAREA"))
        assertEquals(null, TurnKinds.of("TURN_STRAIGHT"))
        assertEquals(null, TurnKinds.of("TURN_KEEP_L"))
        assertEquals(null, TurnKinds.of("SOMETHING_NEW"))
    }
}
