package com.downshift.app.ui.garage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.downshift.app.DownshiftApp
import com.downshift.app.adapters.audio.MicRecorder
import com.downshift.app.adapters.demo.DemoBus
import com.downshift.app.adapters.demo.DemoCommand
import com.downshift.app.core.voice.VoiceCheck
import com.downshift.app.core.voice.VoiceSlot
import com.downshift.app.core.voice.VoiceVerdict
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TakePhase { GUIDE, COUNTDOWN, RECORDING, CHECKED, FAILED }

/** The record sheet: closed while slot is null. level is the latest 50 ms peak, 0..1. */
data class TakeUi(
    val slot: VoiceSlot? = null,
    val phase: TakePhase = TakePhase.GUIDE,
    val count: Int = 0,
    val level: Float = 0f,
    val verdict: VoiceVerdict? = null,
    val failure: String? = null,
    val playing: Boolean = false,
)

/**
 * The record sheet's steps (spec 5): the guide, 3·2·1, a take of 2.5 s while parked (the engine stops first so the
 * cabin speakers stay out of the take), the check, a preview of the take, and keeping it, which turns its card on.
 * After the whistle comes the blow-off, which may be skipped. adb "voice open" opens it for rehearsals and the video.
 */
class VoiceViewModel(app: Application) : AndroidViewModel(app) {
    private val voice = (app as DownshiftApp).voice
    private val host = (app as DownshiftApp).host
    private val _ui = MutableStateFlow(TakeUi())
    val ui: StateFlow<TakeUi> = _ui.asStateFlow()
    private var take: FloatArray? = null
    private var job: Job? = null

    init {
        viewModelScope.launch {
            DemoBus.commands.collect { c -> if (c is DemoCommand.Voice && c.action == "open") c.slot?.let(VoiceSlot::byKey)?.let(::open) }
        }
    }

    fun open(slot: VoiceSlot) {
        job?.cancel()
        host.cancelAudition()
        take = null
        _ui.value = TakeUi(slot = slot)
    }

    fun close() {
        job?.cancel()
        host.cancelAudition()
        take = null
        _ui.value = TakeUi()
    }

    fun record() {
        val slot = _ui.value.slot ?: return
        if (!host.parked) return
        job?.cancel()
        host.cancelAudition()
        if (host.engineOn) host.stop()
        take = null
        job = viewModelScope.launch {
            for (n in 3 downTo 1) {
                _ui.update { it.copy(phase = TakePhase.COUNTDOWN, count = n, verdict = null, failure = null) }
                delay(COUNT_MS)
            }
            _ui.update { it.copy(phase = TakePhase.RECORDING, level = 0f) }
            when (val r = voice.take { lv -> _ui.update { it.copy(level = lv) } }) {
                is MicRecorder.Result.Done -> {
                    val c = VoiceCheck.clean(r.samples, slot)
                    take = c.samples.takeIf { c.verdict == VoiceVerdict.OK }
                    _ui.update { it.copy(phase = TakePhase.CHECKED, verdict = c.verdict, level = 0f) }
                }
                MicRecorder.Result.Cancelled -> _ui.update { it.copy(phase = TakePhase.GUIDE, level = 0f) } // the car moved: dropped
                MicRecorder.Result.Failed -> _ui.update { it.copy(phase = TakePhase.FAILED, failure = MIC_BUSY, level = 0f) }
            }
        }
    }

    fun permissionDenied() = _ui.update { it.copy(phase = TakePhase.FAILED, failure = NO_PERMISSION) }

    /** The tab's preview with this take (spec 5 "들어보기"); again stops it. */
    fun listen() {
        val slot = _ui.value.slot ?: return
        val t = take ?: return
        if (_ui.value.playing) {
            host.cancelAudition()
            return
        }
        _ui.update { it.copy(playing = true) }
        voice.audition(slot.category, mapOf(slot to t)) { _ui.update { it.copy(playing = false) } }
    }

    /** "이걸로 쓸게요": saved, card on, then the blow-off after the whistle or the sheet closes. */
    fun keep() {
        val slot = _ui.value.slot ?: return
        val t = take ?: return
        host.cancelAudition()
        voice.keep(slot, t)
        next(slot)
    }

    fun skip() {
        _ui.value.slot?.let(::next)
    }

    private fun next(slot: VoiceSlot) = if (slot == VoiceSlot.WHISTLE) open(VoiceSlot.BLOW_OFF) else close()

    override fun onCleared() = host.cancelAudition()

    companion object {
        const val COUNT_MS = 800L
        const val MIC_BUSY = "지금은 마이크를 쓸 수 없어요. 잠시 뒤에 다시 해 주세요"
        const val NO_PERMISSION = "마이크 권한이 있어야 녹음할 수 있어요"
    }
}
