package com.downshift.app.core.flow

import com.downshift.app.core.model.UxState

/**
 * What the car's driving restrictions leave open (Pleos driving restriction guide: an app that declares DO applies them
 * itself). The drive screen is the DO screen. The garage, the start screen, the summary and the safety note are for a
 * parked car: while DO is asked for, a lock view stands in for them and the garage neither changes nor previews.
 */
object UxGate {
    /**
     * How long a parked-only screen stays before the lock view takes its place. Leaving park asks for DO a moment
     * before the trip opens the drive screen (up to one idle tick of the drive thread, 500 ms); without the wait the
     * lock view flashes between the start screen and the drive screen. A start that is already restricted locks at once.
     */
    const val LOCK_DELAY_MS = 800L

    /** The screen may show as it is; otherwise the lock view stands in for it. */
    fun shows(screen: Screen, ux: UxState): Boolean = !ux.requiresDo || screen == Screen.DRIVE

    /** The garage may change things and play previews: the gear and the speed say still, and the car asks for no DO. */
    fun parked(stopped: Boolean, ux: UxState): Boolean = stopped && !ux.requiresDo
}
