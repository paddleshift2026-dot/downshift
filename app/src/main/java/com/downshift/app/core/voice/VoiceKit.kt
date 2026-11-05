package com.downshift.app.core.voice

import com.downshift.app.core.garage.Category

/**
 * What the engine plays from the user's takes, at the engine's output rate (spec 6.3). A null field stays synthetic.
 * The names are the mixer's keys for the whistle loop and the one-shots.
 */
class VoiceKit(
    val engine: List<FloatArray>? = null,
    val whistle: FloatArray? = null,
    val blowOff: FloatArray? = null,
    val pop: FloatArray? = null,
    val shift: FloatArray? = null,
) {
    val empty: Boolean get() = engine == null && whistle == null && blowOff == null && pop == null && shift == null

    fun clips(): Map<String, FloatArray> = buildMap {
        whistle?.let { put(WHISTLE, it) }
        blowOff?.let { put(BLOW_OFF, it) }
        pop?.let { put(POP, it) }
        shift?.let { put(SHIFT, it) }
    }

    /** For the log and show.sh: grains for the engine, samples for the rest. */
    override fun toString() =
        "engine=${engine?.size ?: 0} whistle=${whistle?.size ?: 0} blowoff=${blowOff?.size ?: 0} pop=${pop?.size ?: 0} shift=${shift?.size ?: 0}"

    companion object {
        const val WHISTLE = "voice:whistle"
        const val BLOW_OFF = "voice:blowoff"
        const val POP = "voice:pop"
        const val SHIFT = "voice:shift"
        val NONE = VoiceKit()

        /** The cleaned takes (at [from] Hz) of the cards that are [on], cut and moved to [rate] Hz. */
        fun of(recordings: Map<VoiceSlot, FloatArray>, on: Set<Category>, rate: Int, from: Int = VoiceCheck.RATE): VoiceKit {
            fun take(slot: VoiceSlot) = recordings[slot]?.takeIf { slot.category in on && it.isNotEmpty() }
            return VoiceKit(
                engine = take(VoiceSlot.ENGINE)?.let { r ->
                    runCatching { VoiceCut.grains(r, from).map { VoiceCut.resample(it, from, rate) } }.getOrNull()
                },
                whistle = take(VoiceSlot.WHISTLE)?.let { VoiceCut.resample(VoiceCut.loop(it, from), from, rate) },
                blowOff = take(VoiceSlot.BLOW_OFF)?.let { VoiceCut.resample(it, from, rate) },
                pop = take(VoiceSlot.POP)?.let { VoiceCut.resample(it, from, rate) },
                shift = take(VoiceSlot.SHIFT)?.let { VoiceCut.resample(it, from, rate) },
            )
        }
    }
}
