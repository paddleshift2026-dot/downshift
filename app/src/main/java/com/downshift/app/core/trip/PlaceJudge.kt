package com.downshift.app.core.trip

import kotlin.math.roundToInt

/**
 * School zones, speed cameras and section cameras on a trip. Each announced place keeps its distance to go, which every
 * step shortens by the distance driven (the navigation's own figure replaces it whenever it comes again). A camera is
 * kept when the top speed over its last CAMERA_WINDOW_M stayed within its limit, a school zone when the top speed over
 * the ZONE_M after its start did (30 km/h unless the navigation gave a limit), a section when its average speed did.
 * Places without a limit are not counted, and each id is judged once.
 */
class PlaceJudge {
    private class Pending(val kind: PlaceKind, var limit: Int, var remain: Double, var maxKph: Int = 0)
    private class Zone(val limit: Int, var insideM: Double, var maxKph: Int)
    private class Section(val limit: Int, var metres: Double = 0.0, var seconds: Double = 0.0)

    private val ahead = LinkedHashMap<String, Pending>()
    private val judged = HashSet<String>()
    private val zones = ArrayList<Zone>()
    private var section: Section? = null

    var passed = 0
        private set
    var kept = 0
        private set

    /** Within APPROACH_M of a school zone, or inside one: the engine sound is turned down. */
    val schoolZone: Boolean
        get() = zones.isNotEmpty() || ahead.values.any { it.kind == PlaceKind.SCHOOL_ZONE && it.remain <= APPROACH_M }

    fun announce(p: PlaceAhead) {
        if (p.id in judged) return
        val known = ahead[p.id]
        if (known == null) {
            ahead[p.id] = Pending(p.kind, p.limitKph, p.remainM)
        } else {
            known.remain = p.remainM
            if (p.limitKph > 0) known.limit = p.limitKph
        }
    }

    fun step(speedMps: Double, dtSec: Double) {
        val d = speedMps.coerceAtLeast(0.0) * dtSec
        val kph = (speedMps * 3.6).roundToInt()
        // Zones and the section already under way first, so one opened below does not count this step twice.
        val open = zones.iterator()
        while (open.hasNext()) {
            val zone = open.next()
            zone.insideM += d
            zone.maxKph = maxOf(zone.maxKph, kph)
            if (zone.insideM >= ZONE_M) {
                judge(zone.maxKph <= zone.limit)
                open.remove()
            }
        }
        section?.let { s ->
            s.metres += d
            s.seconds += dtSec
        }
        val next = ahead.entries.iterator()
        while (next.hasNext()) {
            val (id, p) = next.next()
            p.remain -= d
            if (p.kind == PlaceKind.CAMERA && p.remain <= CAMERA_WINDOW_M) p.maxKph = maxOf(p.maxKph, kph)
            if (p.remain > 0) continue
            next.remove()
            judged += id
            when (p.kind) {
                PlaceKind.CAMERA -> if (p.limit > 0) judge(p.maxKph <= p.limit)
                PlaceKind.SCHOOL_ZONE -> zones += Zone(if (p.limit > 0) p.limit else SCHOOL_LIMIT_KPH, -p.remain, kph)
                PlaceKind.SECTION_START -> section = Section(p.limit)
                PlaceKind.SECTION_END -> section?.let { s ->
                    val limit = if (p.limit > 0) p.limit else s.limit
                    if (limit > 0 && s.seconds > 0) judge((s.metres / s.seconds * 3.6).roundToInt() <= limit)
                    section = null
                }
            }
        }
    }

    /** The trip is over: a school zone we are in is judged on what was driven of it, an open section is dropped. */
    fun finish() {
        zones.forEach { judge(it.maxKph <= it.limit) }
        zones.clear()
        section = null
        ahead.clear()
    }

    private fun judge(ok: Boolean) {
        passed++
        if (ok) kept++
    }

    private companion object {
        const val CAMERA_WINDOW_M = 50.0
        const val ZONE_M = 300.0 // school zones reach 300 m from the school gate
        const val APPROACH_M = 100.0
        const val SCHOOL_LIMIT_KPH = 30
    }
}
