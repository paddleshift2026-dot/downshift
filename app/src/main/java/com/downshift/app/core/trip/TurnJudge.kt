package com.downshift.app.core.trip

import kotlin.math.abs

/**
 * Turn signals ahead of the navigation's turns. One turn waits at a time, its distance to go shortened by every step.
 * It scores when the stalk points the turn's way from the moment the turn comes within its lead (30 m, 100 m on a
 * highway: the Road Traffic Act's signalling distances) all the way to the turn point. An announcement of the same
 * turn only corrects the distance; a different one replaces it, as after a re-route. A repeat of the turn just taken
 * is ignored.
 */
class TurnJudge {
    private class Pending(val side: Side, val highway: Boolean, var remain: Double, var armed: Boolean = false, var ok: Boolean = false)

    private var pending: Pending? = null
    private var last: Pair<Side, Boolean>? = null
    private var sinceLastM = Double.MAX_VALUE

    var turns = 0
        private set
    var signalled = 0
        private set

    fun announce(t: TurnAhead) {
        val p = pending
        if (p != null && p.side == t.side && p.highway == t.highway && abs(p.remain - t.remainM) <= SAME_TURN_M) {
            p.remain = t.remainM
            return
        }
        if (p == null && last == (t.side to t.highway) && t.remainM <= REPEAT_M && sinceLastM < REPEAT_WINDOW_M) return
        pending = Pending(t.side, t.highway, t.remainM)
    }

    fun step(speedMps: Double, signal: Signal, dtSec: Double) {
        val d = speedMps.coerceAtLeast(0.0) * dtSec
        sinceLastM += d
        val p = pending ?: return
        p.remain -= d
        if (p.remain <= if (p.highway) HIGHWAY_LEAD_M else CITY_LEAD_M) {
            val on = signal.shows(p.side)
            if (!p.armed) {
                p.armed = true
                p.ok = on
            } else if (!on) {
                p.ok = false
            }
        }
        if (p.remain > 0) return
        turns++
        if (p.ok) signalled++
        pending = null
        last = p.side to p.highway
        sinceLastM = 0.0
    }

    private companion object {
        const val CITY_LEAD_M = 30.0
        const val HIGHWAY_LEAD_M = 100.0
        const val SAME_TURN_M = 60.0
        const val REPEAT_M = 15.0
        const val REPEAT_WINDOW_M = 50.0
    }
}
