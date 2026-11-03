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

/**
 * The web pedal with more than one page open (two tabs, the presenter's and a judge's): each page has its own link and
 * the pedal is the deepest of them, so a page that closes or goes quiet lets go of its own press only. Returns the
 * combined value when it changes.
 */
class PedalMix<K>(private val staleMs: Long = 1500) {
    private val links = HashMap<K, PedalLink>()

    @Volatile var pct = 0
        private set

    @Synchronized
    fun onPedal(page: K, pct: Int, nowMs: Long): Int? {
        link(page).onPedal(pct, nowMs)
        return combine()
    }

    @Synchronized
    fun onMessage(page: K, nowMs: Long) = link(page).onMessage(nowMs)

    @Synchronized
    fun tick(nowMs: Long): Int? {
        links.values.forEach { it.tick(nowMs) }
        return combine()
    }

    @Synchronized
    fun onClose(page: K): Int? {
        links.remove(page) ?: return null
        return combine()
    }

    @Synchronized
    fun clear(): Int? {
        links.clear()
        return combine()
    }

    private fun link(page: K) = links.getOrPut(page) { PedalLink(staleMs) }

    private fun combine(): Int? {
        val next = links.values.maxOfOrNull { it.pct } ?: 0
        if (next == pct) return null
        pct = next
        return next
    }
}
