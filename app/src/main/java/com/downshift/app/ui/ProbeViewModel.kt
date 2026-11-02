package com.downshift.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.downshift.app.adapters.audio.FocusMode
import com.downshift.app.adapters.audio.FocusProbe
import com.downshift.app.adapters.audio.LoopPlayer
import com.downshift.app.adapters.audio.trackUsage
import com.downshift.app.adapters.car.CarPropertyProbe
import com.downshift.app.adapters.demo.DemoBus
import com.downshift.app.adapters.demo.DemoCommand
import com.downshift.app.adapters.pleos.PleosProbe
import com.downshift.app.core.audio.LatencyStats
import com.downshift.app.core.audio.TestTone
import com.downshift.app.core.audio.WavLoader
import com.downshift.app.core.probe.ProbeLedger
import com.downshift.app.core.probe.ProbeRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProbeViewModel(app: Application) : AndroidViewModel(app) {
    private val ledger = ProbeLedger()
    private val latency = LatencyStats()
    private val car = CarPropertyProbe(app, ledger)
    private val pleos = PleosProbe(app, ledger)
    private val player = LoopPlayer(app, latency)
    private val focus = FocusProbe(app, ledger)
    private var configDump = ""
    private var focusMode = FocusMode.NONE

    private val _rows = MutableStateFlow<List<ProbeRow>>(emptyList())
    val rows: StateFlow<List<ProbeRow>> = _rows.asStateFlow()
    private val _audio = MutableStateFlow("")
    val audio: StateFlow<String> = _audio.asStateFlow()

    init {
        configDump = car.start()
        pleos.start()
        focus.start()
        playTone()
        viewModelScope.launch {
            while (isActive) {
                _rows.value = ledger.rows()
                _audio.value = audioLine()
                delay(500)
            }
        }
        viewModelScope.launch { DemoBus.commands.collect { handle(it) } }
    }

    fun setThrottle(t: Double) = player.setThrottle(t)

    fun setFocus(mode: FocusMode) {
        focusMode = mode
        focus.request(mode)
        player.restart(mode.trackUsage())
    }

    fun playGuidance() = focus.playGuidance()

    fun requestRoute() {
        pleos.requestDemoRoute()
    }

    fun writeReport(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date())
        val text = buildString {
            appendLine("# Downshift M0 프로브 보고서")
            appendLine()
            appendLine("생성: $stamp")
            appendLine()
            appendLine("## 신호")
            appendLine()
            append(ledger.toMarkdown())
            appendLine()
            appendLine("## 오디오")
            appendLine()
            appendLine("- ${audioLine()}")
            appendLine()
            appendLine("## 앱에서 보이는 AAOS 속성 (${if (configDump.isEmpty()) 0 else configDump.lines().size}개)")
            appendLine()
            appendLine("```")
            appendLine(configDump)
            appendLine("```")
        }
        File(getApplication<Application>().filesDir, "m0_report.md").writeText(text)
        Log.i(REPORT_TAG, "=== REPORT BEGIN ===")
        text.lines().forEach { Log.i(REPORT_TAG, it.ifEmpty { " " }) }
        Log.i(REPORT_TAG, "=== REPORT END ===")
        return text
    }

    private fun handle(c: DemoCommand) {
        when (c) {
            is DemoCommand.Throttle -> setThrottle(c.value)
            is DemoCommand.Focus -> setFocus(c.mode)
            DemoCommand.Guide -> playGuidance()
            DemoCommand.Route -> requestRoute()
            DemoCommand.Report -> writeReport()
            is DemoCommand.Loop -> loadLoop(c.name)
            DemoCommand.Tone -> playTone()
        }
    }

    private fun playTone() {
        player.start(TestTone.engineLoop(48_000, TestTone.firingHz(900.0, 4)), 48_000, focusMode.trackUsage())
    }

    /** Listening test: loops copied into app/src/debug/assets/loops (git-ignored, licensed audio). */
    private fun loadLoop(name: String) {
        runCatching { getApplication<Application>().assets.open("loops/$name").use { WavLoader.load(it.readBytes()) } }
            .onSuccess { w ->
                player.start(w.mono, w.sampleRate, focusMode.trackUsage())
                ledger.record("audio.loop", "$name ${w.sampleRate}Hz ${w.mono.size}f", System.currentTimeMillis())
            }
            .onFailure { ledger.unsupported("audio.loop", "$name: ${it.message}") }
    }

    private fun audioLine(): String {
        fun f(v: Double?) = v?.let { "%.0f".format(it) } ?: "-"
        return "${player.bufferInfo} | 페달→출력 p50 ${f(latency.p50())} / p95 ${f(latency.p95())} / max ${f(latency.max())} ms (n=${latency.count})"
    }

    override fun onCleared() {
        player.stop()
        focus.stop()
        pleos.stop()
        car.stop()
    }

    private companion object {
        const val REPORT_TAG = "DownshiftReport"
    }
}
