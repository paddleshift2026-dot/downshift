package com.downshift.app.adapters.store

import android.content.Context
import com.downshift.app.core.audio.WavLoader
import com.downshift.app.core.dsp.WavWriter
import com.downshift.app.core.voice.VoiceCheck
import com.downshift.app.core.voice.VoiceCut
import com.downshift.app.core.voice.VoiceSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The user's cleaned takes (spec 7): one WAV per slot in the app's private files/voice/, at VoiceCheck.RATE. Nothing
 * here leaves the car; uninstalling the app removes them.
 */
class VoiceStore(context: Context) {
    private val dir = File(context.filesDir, "voice").apply { mkdirs() }
    private val _recorded = MutableStateFlow(scan())
    /** The slots that have a take. */
    val recorded: StateFlow<Set<VoiceSlot>> = _recorded.asStateFlow()

    fun file(slot: VoiceSlot) = File(dir, "${slot.key}.wav")

    fun load(slot: VoiceSlot): FloatArray? = runCatching {
        val w = WavLoader.load(file(slot).readBytes())
        if (w.sampleRate == VoiceCheck.RATE) w.mono else VoiceCut.resample(w.mono, w.sampleRate, VoiceCheck.RATE)
    }.getOrNull()

    fun loadAll(): Map<VoiceSlot, FloatArray> = _recorded.value.mapNotNull { s -> load(s)?.let { s to it } }.toMap()

    fun save(slot: VoiceSlot, samples: FloatArray) {
        file(slot).writeBytes(WavWriter.pcm16Mono(samples, samples.size, VoiceCheck.RATE))
        _recorded.value = scan()
    }

    fun deleteAll() {
        VoiceSlot.entries.forEach { file(it).delete() }
        _recorded.value = scan()
    }

    private fun scan(): Set<VoiceSlot> = VoiceSlot.entries.filter { file(it).isFile }.toSet()
}
