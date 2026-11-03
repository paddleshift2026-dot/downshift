package com.downshift.app.core.garage

/** The four sound-character bars on the garage screen, each 0..10. */
data class SoundBars(val bass: Int, val rasp: Int, val whistle: Int, val pop: Int)

/**
 * The design's `sound()`: bass and rasp start from the engine, whistle from 0 and pops from 2; the intake, exhaust and
 * transmission add theirs, the pops step moves the pops bar, and every bar is clamped to 0..10.
 */
object SoundCharacter {
    const val MAX = 10
    private const val POP_BASE = 2
    private val NEUTRAL_POPS = Tune().pops

    /** With [previewId], the bars of the build with that part fitted; placeholders and limited packs change nothing. */
    fun of(s: GarageState, previewId: String? = null): SoundBars {
        val swap = previewId?.let(Catalog::part)?.takeIf { it.kind == PartKind.NORMAL }
        val fx = Category.entries.mapNotNull { c ->
            if (swap != null && swap.category == c) swap.fx else Catalog.part(Garage.fitted(s, c))?.fx
        }
        fun bar(base: Int, pick: (SoundFx) -> Int) = (base + fx.sumOf(pick)).coerceIn(0, MAX)
        return SoundBars(
            bass = bar(0) { it.bass },
            rasp = bar(0) { it.rasp },
            whistle = bar(0) { it.whistle },
            pop = bar(POP_BASE + s.tune.pops - NEUTRAL_POPS) { it.pop },
        )
    }
}
