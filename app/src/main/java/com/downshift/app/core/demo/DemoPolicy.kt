package com.downshift.app.core.demo

/**
 * What demo mode lets through (PRD feature G: demo points are kept apart from the real balance). Outside demo mode the
 * real wallet changes only through real driving and the driver's own taps, so adb injections that would change a
 * trip's points, and garage moves that make points out of nothing, are dropped; the flow, the sound and the garage taps
 * stay reachable for checks.
 */
object DemoPolicy {
    const val MAX_LAPSE = 50

    /** adb drive events that change a trip's points. */
    val POINT_EVENTS = setOf("limit", "camera", "section", "school", "turn", "lapse")

    /** adb garage actions that make points out of nothing. */
    val WALLET_ACTIONS = setOf("add", "reset")

    fun allowsDrive(event: String, demo: Boolean): Boolean = demo || event !in POINT_EVENTS

    fun allowsGarage(action: String, demo: Boolean): Boolean = demo || action !in WALLET_ACTIONS

    /** The demo's distance lapse; always ×1 outside demo mode. */
    fun lapse(requested: Int, demo: Boolean): Int = if (demo) requested.coerceIn(1, MAX_LAPSE) else 1
}
