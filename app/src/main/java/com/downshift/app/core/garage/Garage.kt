package com.downshift.app.core.garage

import com.downshift.app.core.build.Build
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class PartStatus { EQUIPPED, OWNED, UNLOCKABLE, LOCKED, PLACEHOLDER, LIMITED }

/** The garage's rules: what a part's state is, unlocking with points, fitting owned parts, and what to aim for next. */
object Garage {
    /** The economy baseline from the PRD: about 7.5 points per safely driven km. */
    const val POINTS_PER_KM = 7.5

    fun fitted(s: GarageState, category: Category): String = when (category) {
        Category.ENGINE -> s.engine
        Category.INTAKE -> s.intake
        Category.EXHAUST -> s.exhaust
        Category.TRANSMISSION -> s.transmission
    }

    fun status(s: GarageState, p: Part): PartStatus = when {
        p.kind == PartKind.PLACEHOLDER -> PartStatus.PLACEHOLDER
        p.kind == PartKind.LIMITED -> PartStatus.LIMITED
        fitted(s, p.category) == p.id -> PartStatus.EQUIPPED
        p.id in s.owned -> PartStatus.OWNED
        s.balance >= p.price -> PartStatus.UNLOCKABLE
        else -> PartStatus.LOCKED
    }

    /** Spends the part's price and adds it to the owned list, or null when it cannot be unlocked. */
    fun unlock(s: GarageState, id: String): GarageState? {
        val p = Catalog.part(id) ?: return null
        if (p.kind != PartKind.NORMAL || id in s.owned || s.balance < p.price) return null
        return s.copy(balance = s.balance - p.price, owned = s.owned + id)
    }

    /** Fits an owned part, or null when it is not owned or not a part that can be fitted. */
    fun equip(s: GarageState, id: String): GarageState? {
        val p = Catalog.part(id) ?: return null
        if (p.kind != PartKind.NORMAL || id !in s.owned) return null
        return when (p.category) {
            Category.ENGINE -> s.copy(engine = id)
            Category.INTAKE -> s.copy(intake = id)
            Category.EXHAUST -> s.copy(exhaust = id)
            Category.TRANSMISSION -> s.copy(transmission = id)
        }
    }

    fun build(s: GarageState): Build {
        var b = Build()
        for (c in Category.entries) {
            val p = Catalog.part(fitted(s, c)) ?: continue
            b = Catalog.apply(b, p)
        }
        return b.copy(pops = s.tune.pops / 5.0, redlineOffset = s.tune.redlineOffset, volume = s.tune.volume / 100.0)
    }

    /** The fitted build with one part swapped in, for listening before buying; the state itself is untouched. */
    fun preview(s: GarageState, id: String): Build {
        val p = Catalog.part(id) ?: return build(s)
        return Catalog.apply(build(s), p)
    }

    /** The licence-plate name tag, e.g. "I4 · TURBO 2 · SPORT · DCT". */
    fun plate(b: Build): String = Category.entries.joinToString(" · ") { Catalog.part(Catalog.idOf(b, it))!!.code }

    /** Points as safely driven km, rounded up and never "0 km". */
    fun kmFor(points: Int): Int = maxOf(1, ceil(points / POINTS_PER_KM).toInt())

    /**
     * What to aim for: the cheapest part still out of reach; when everything left is affordable, the priciest of
     * those; null once everything is owned.
     */
    fun nextTarget(s: GarageState): Part? {
        val left = Catalog.ALL.filter { it.kind == PartKind.NORMAL && it.id !in s.owned }
        return left.filter { it.price > s.balance }.minByOrNull { it.price } ?: left.maxByOrNull { it.price }
    }

    fun addPoints(s: GarageState, n: Int): GarageState = s.copy(balance = (s.balance + n).coerceAtLeast(0))

    /** Applies a tune, snapped to its steps and ranges. */
    fun tune(s: GarageState, t: Tune): GarageState = s.copy(
        tune = Tune(
            pops = t.pops.coerceIn(0, MAX_POPS),
            redlineOffset = ((t.redlineOffset / 100.0).roundToInt() * 100).coerceIn(-MAX_REDLINE_OFFSET, MAX_REDLINE_OFFSET),
            volume = ((t.volume / 10.0).roundToInt() * 10).coerceIn(0, 100),
        ),
    )

    const val MAX_POPS = 5
    const val MAX_REDLINE_OFFSET = 500
}
