package com.downshift.app.core.sound

import com.downshift.app.core.build.ExhaustPart

/** Which clips an engine pack has and the rpm each loop was recorded at. on = under load, off = coasting. */
data class Voicing(
    val idleClip: String,
    val idleRpm: Double,
    val onClips: List<String>,
    val onRpms: List<Double>,
    val offClips: List<String>,
    val offRpms: List<Double>,
    val startup: String?,
    val shutdown: String?,
    val limiter: String?,
)

/** A looping clip played at rate (1.0 = as recorded). */
data class LoopVoice(val clip: String, val rate: Double, val gain: Double)

/** A clip played once from the start; it keeps playing through later plans and carries its own gain. */
data class OneShot(val clip: String, val gain: Double)

data class SynthVoices(val whistleHz: Double, val whistleGain: Double, val whineHz: Double, val whineGain: Double) {
    companion object {
        val SILENT = SynthVoices(0.0, 0.0, 0.0, 0.0)
    }
}

/**
 * What to play for one audio block. master scales the loops, pops and synth voices; loudness scales the loops
 * further with load and rpm. One-shots start once and are not cut by later plans.
 */
data class SoundPlan(
    val loops: List<LoopVoice>,
    val shots: List<OneShot>,
    val pops: List<Pop>,
    val blowOff: Boolean,
    val synth: SynthVoices,
    val exhaust: ExhaustPart,
    val master: Double,
    val loudness: Double,
) {
    companion object {
        val SILENT = SoundPlan(emptyList(), emptyList(), emptyList(), false, SynthVoices.SILENT, ExhaustPart.STOCK, 0.0, 0.0)
    }
}
