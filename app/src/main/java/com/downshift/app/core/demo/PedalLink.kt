package com.downshift.app.core.demo

/**
 * The web pedal link: which pedal value to press. A value is passed on only when it changes. The page pings every
 * 0.5 s; when nothing has come for staleMs while the pedal is down (a closed tab, a dropped adb forward), the pedal is
 * let go once, so the demo car does not keep accelerating. Messages arrive on the server's threads and ticks on a
 * timer, so every move is synchronized.
 */
class PedalLink(private val staleMs: Long = 1500) {
    private var lastMs = 0L

    @Volatile var pct = 0
        private set

    @Synchronized
    fun onPedal(pct: Int, nowMs: Long): Int? {
        lastMs = nowMs
        if (pct == this.pct) return null
        this.pct = pct
        return pct
    }

    @Synchronized
    fun onMessage(nowMs: Long) {
        lastMs = nowMs
    }

    @Synchronized
    fun tick(nowMs: Long): Int? = if (pct > 0 && nowMs - lastMs > staleMs) release() else null

    @Synchronized
    fun onClose(): Int? = if (pct > 0) release() else null

    private fun release(): Int {
        pct = 0
        return 0
    }
}
