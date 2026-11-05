package com.downshift.app

import android.app.Application
import android.util.Log
import com.downshift.app.adapters.audio.AndroidMic
import com.downshift.app.adapters.audio.FileMic
import com.downshift.app.adapters.audio.MicRecorder
import com.downshift.app.adapters.demo.DemoBus
import com.downshift.app.adapters.demo.DemoCommand
import com.downshift.app.adapters.store.GarageRepository
import com.downshift.app.adapters.store.VoiceStore
import com.downshift.app.core.audio.WavLoader
import com.downshift.app.core.garage.Category
import com.downshift.app.core.garage.Garage
import com.downshift.app.core.voice.VoiceCheck
import com.downshift.app.core.voice.VoiceCut
import com.downshift.app.core.voice.VoiceSlot
import com.downshift.app.core.voice.VoiceVerdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The voice parts (docs/superpowers/specs/2026-11-05-voice-parts-design.md): the takes (VoiceStore), the voice cards
 * that are on (GarageState.voiceOn of the wallet in use), a new take from the microphone or a stand-in file, and the
 * adb voice commands, with the engine's VoiceKit kept in step. Made with the process so adb works with no screen up.
 */
class VoiceHost(app: Application, private val garage: GarageRepository, private val engine: EngineHost) {
    private val tag = "DownshiftLab"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val inbox: File? = app.getExternalFilesDir("voice_in")
    val store = VoiceStore(app)
    val recorded: StateFlow<Set<VoiceSlot>> = store.recorded

    @Volatile private var standIn: File? = null
    private val recorder = MicRecorder { standIn?.also { standIn = null }?.let { FileMic(it) } ?: AndroidMic() }

    init {
        scope.launch {
            combine(garage.state.map { it.voiceOn }.distinctUntilChanged(), store.recorded) { on, _ -> on }.collect { on ->
                val recordings = withContext(Dispatchers.IO) { store.loadAll() }
                engine.setVoices(recordings, on)
            }
        }
        scope.launch { DemoBus.commands.collect { if (it is DemoCommand.Voice) handle(it) } }
    }

    /** One take of VoiceCheck.RECORD_SEC while the car stays parked (spec 8); [level] is called on a background thread. */
    suspend fun take(level: (Float) -> Unit): MicRecorder.Result = withContext(Dispatchers.IO) {
        recorder.record(VoiceCheck.RECORD_SEC, VoiceCheck.RATE, keepGoing = { engine.parked }, level = level)
    }

    /** "이걸로 쓸게요": the cleaned take saved and its card on (spec 4). */
    fun keep(slot: VoiceSlot, cleaned: FloatArray) {
        store.save(slot, cleaned)
        setOn(slot.category, true)
    }

    /** False when the card has no take yet and cannot come on. */
    fun setOn(category: Category, on: Boolean): Boolean {
        val withTakes = VoiceSlot.categories(store.recorded.value)
        var done = false
        garage.update { s -> Garage.setVoice(s, category, on, withTakes)?.also { done = true } ?: s }
        return done
    }

    /** Every take deleted and every voice card off, in both wallets (spec 7). */
    fun deleteAll() {
        store.deleteAll()
        garage.updateAll { it.copy(voiceOn = emptySet()) }
    }

    /** [category]'s preview with [take] (not kept yet), or with the saved takes when null; heard even with the card off. */
    fun audition(category: Category, take: Map<VoiceSlot, FloatArray>?, onDone: () -> Unit) {
        val voice = take ?: VoiceSlot.of(category).mapNotNull { s -> store.load(s)?.let { s to it } }.toMap()
        engine.audition(Garage.build(garage.state.value), category, voice, onDone)
    }

    private fun handle(c: DemoCommand.Voice) {
        val slot = c.slot?.let(VoiceSlot::byKey)
        val file = c.file?.let { name -> inbox?.let { File(it, name) } }
        when (c.action) {
            "mic" -> {
                standIn = file?.takeIf { it.isFile }
                Log.i(tag, "voice mic ${standIn?.name ?: "microphone"}")
            }
            "load" -> if (slot != null && file != null) scope.launch { load(slot, file) }
            "on", "off" -> {
                val category = c.tab?.let { TABS[it] } ?: return
                Log.i(tag, "voice ${c.action} $category ${setOn(category, c.action == "on")}")
            }
            "clear" -> {
                deleteAll()
                Log.i(tag, "voice clear")
            }
            else -> Unit // "open" belongs to the record sheet (VoiceViewModel)
        }
    }

    /** A stand-in take straight into a slot, through the same check as a real one. */
    private suspend fun load(slot: VoiceSlot, file: File) {
        val raw = withContext(Dispatchers.IO) { runCatching { WavLoader.load(file.readBytes()) }.getOrNull() }
        if (raw == null) {
            Log.i(tag, "voice load ${slot.key} unreadable")
            return
        }
        val mono = if (raw.sampleRate == VoiceCheck.RATE) raw.mono else VoiceCut.resample(raw.mono, raw.sampleRate, VoiceCheck.RATE)
        val c = VoiceCheck.clean(mono, slot)
        Log.i(tag, "voice load ${slot.key} ${c.verdict}")
        if (c.verdict == VoiceVerdict.OK) keep(slot, c.samples)
    }

    private companion object {
        val TABS = mapOf("engine" to Category.ENGINE, "intake" to Category.INTAKE, "exhaust" to Category.EXHAUST, "trans" to Category.TRANSMISSION)
    }
}
