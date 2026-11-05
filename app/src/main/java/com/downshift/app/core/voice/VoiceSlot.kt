package com.downshift.app.core.voice

import com.downshift.app.core.garage.Category

/**
 * The five recordings of the voice parts (docs/superpowers/specs/2026-11-05-voice-parts-design.md, 3): the garage tab
 * each belongs to, whether it is sustained (cut into grains or a loop) or a one-shot, the longest a one-shot may play,
 * and what the record sheet tells the user.
 */
enum class VoiceSlot(
    val category: Category,
    val sustained: Boolean,
    val maxSec: Double,
    val title: String,
    val how: String,
    val use: String,
) {
    ENGINE(Category.ENGINE, true, 2.5, "엔진 소리", "입술을 떨며 \"부르르르\" 2초, 같은 크기로", "실린더가 터질 때마다 한 토막씩"),
    WHISTLE(Category.INTAKE, true, 2.5, "터보 휘슬", "\"쉬이이—\" 바람 새는 소리 2초", "터보가 차오를수록 높고 크게"),
    BLOW_OFF(Category.INTAKE, false, 1.0, "블로오프", "\"피슈!\" 한 번, 짧게", "가속 페달을 뗄 때"),
    POP(Category.EXHAUST, false, 0.4, "배기 팝", "\"팡!\" 또는 \"퍽!\" 한 번, 짧게", "감속할 때 팝콘처럼"),
    SHIFT(Category.TRANSMISSION, false, 0.8, "변속음", "\"치키차\"처럼 변속하는 소리 한 번", "기어가 바뀔 때마다");

    /** The file and adb name: engine, whistle, blowoff, pop, shift. */
    val key: String get() = name.lowercase().replace("_", "")

    companion object {
        fun of(category: Category): List<VoiceSlot> = entries.filter { it.category == category }

        fun byKey(key: String): VoiceSlot? = entries.firstOrNull { it.key == key.lowercase() }

        /** The tabs with at least one of [slots] recorded. */
        fun categories(slots: Set<VoiceSlot>): Set<Category> = slots.map { it.category }.toSet()
    }
}
