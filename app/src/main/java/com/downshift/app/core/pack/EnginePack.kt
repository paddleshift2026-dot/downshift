package com.downshift.app.core.pack

import com.downshift.app.core.build.EngineId
import com.downshift.app.core.engine.EngineSpec
import com.downshift.app.core.sound.BandLayout
import com.downshift.app.core.sound.Voicing
import kotlinx.serialization.Serializable

/** One clip in a pack. rpm is the rpm the loop was recorded at; without it the default ladder is used. */
@Serializable
data class PackClip(val name: String, val file: String, val rpm: Double? = null)

/** manifest.json of assets/engines/<id>/. */
@Serializable
data class EnginePackManifest(val engine: String, val source: String, val license: String = "", val sampleRate: Int, val clips: List<PackClip>)

/** A pack decoded into memory and ready for the mixer. */
class LoadedPack(val engine: EngineId, val sampleRate: Int, val clips: Map<String, FloatArray>, val voicing: Voicing, val source: String)

/**
 * Clip names: idle, on_1..on_7 and off_1..off_7 (1 = idle_low … 7 = very_high in the slaczky packs),
 * startup, engine_off and maxRPM.
 */
object PackRules {
    val BANDS = listOf("idle_low", "low", "low_med", "med", "med_high", "high", "very_high")
    private val ONE_SHOTS = listOf("startup", "engine_off", "maxRPM")
    private val KNOWN = setOf("idle") + ONE_SHOTS + BANDS.indices.flatMap { listOf("on_${it + 1}", "off_${it + 1}") }

    fun voicing(m: EnginePackManifest, spec: EngineSpec): Voicing {
        val byName = m.clips.associateBy { it.name }
        val idleRpm = byName["idle"]?.rpm ?: spec.idleRpm
        val ladder = BandLayout.defaultRpms(idleRpm, spec.redlineRpm)
        fun ladderOf(prefix: String): List<Pair<String, Double>> =
            BANDS.indices.mapNotNull { i -> byName["${prefix}_${i + 1}"]?.let { it.name to (it.rpm ?: ladder[i + 1]) } }.sortedBy { it.second }
        val on = ladderOf("on")
        val off = ladderOf("off")
        return Voicing(
            idleClip = "idle",
            idleRpm = idleRpm,
            onClips = on.map { it.first },
            onRpms = on.map { it.second },
            offClips = off.map { it.first },
            offRpms = off.map { it.second },
            startup = "startup".takeIf { it in byName },
            shutdown = "engine_off".takeIf { it in byName },
            limiter = "maxRPM".takeIf { it in byName },
        )
    }

    /** Everything wrong with a manifest, as messages for the pack builder and loader; empty means usable. */
    fun problems(m: EnginePackManifest): List<String> {
        val out = mutableListOf<String>()
        val names = m.clips.map { it.name }
        if (m.sampleRate <= 0) out += "sampleRate must be positive, got ${m.sampleRate}"
        names.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.forEach { out += "duplicate clip $it" }
        names.filter { it !in KNOWN }.forEach { out += "unknown clip $it" }
        (listOf("idle") + ONE_SHOTS).filter { it !in names }.forEach { out += "missing $it clip" }
        val on = BANDS.indices.map { it + 1 }.filter { "on_$it" in names }
        val off = BANDS.indices.map { it + 1 }.filter { "off_$it" in names }
        if (on.isEmpty()) out += "no on-load loops (on_1..on_7)"
        (on - off.toSet()).forEach { out += "on_$it has no off_$it" }
        (off - on.toSet()).forEach { out += "off_$it has no on_$it" }
        for (prefix in listOf("on", "off")) {
            val given = BANDS.indices.mapNotNull { i -> m.clips.firstOrNull { it.name == "${prefix}_${i + 1}" }?.rpm }
            if (given.zipWithNext().any { (a, b) -> b <= a }) out += "$prefix rpms do not rise with the band number: $given"
        }
        return out
    }
}
