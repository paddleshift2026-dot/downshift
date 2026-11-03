package com.downshift.app.core.pack

import com.downshift.app.core.engine.EngineSpec
import com.downshift.app.core.sound.BandLayout
import com.downshift.app.core.sound.Voicing

/**
 * The engine without recordings: no clips, the real-time pulse voice synthesises it from the firing order.
 * The voicing keeps the default rpm ladder only so loudness and pops still know where idle and the top are.
 */
object ProceduralPack {
    const val SOURCE = "procedural"

    fun build(spec: EngineSpec, sampleRate: Int = 48_000): LoadedPack {
        val rpms = BandLayout.defaultRpms(spec.idleRpm, spec.redlineRpm).drop(1)
        val names = rpms.indices.map { it + 1 }
        val voicing = Voicing("idle", spec.idleRpm, names.map { "on_$it" }, rpms, names.map { "off_$it" }, rpms, null, null, null)
        return LoadedPack(spec.id, sampleRate, emptyMap(), voicing, SOURCE, procedural = true)
    }
}
