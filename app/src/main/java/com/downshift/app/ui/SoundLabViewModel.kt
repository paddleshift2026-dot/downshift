package com.downshift.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.downshift.app.DownshiftApp
import com.downshift.app.core.build.Build
import com.downshift.app.core.input.ThrottleSource
import com.downshift.app.core.model.FuelKind
import com.downshift.app.core.runtime.Telemetry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.round

/** What changes rarely: the build and where things come from. */
data class LabConfig(
    val ready: Boolean = false,
    val build: Build = Build(),
    val packSource: String = "",
    val consoleConnected: Boolean = false,
    val fuel: FuelKind = FuelKind.UNKNOWN,
    val audioInfo: String = "",
    val recording: String? = null,
)

/** What changes five times a second while the engine runs; kept apart so only the gauges redraw. */
data class LabLive(
    val telemetry: Telemetry = Telemetry.OFF,
    val speedKph: Double = 0.0,
    val throttle: Double = 0.0,
    val source: ThrottleSource = ThrottleSource.ESTIMATE,
    val manual: Boolean = false,
    val latency: String = "",
)

/**
 * The sound lab's view of the shared engine ([com.downshift.app.EngineHost]): the build, where the sound comes from,
 * and the gauges. The screen calls the host through here; adb (DemoBus) reaches the host directly.
 */
class SoundLabViewModel(app: Application) : AndroidViewModel(app) {
    private val host = (app as DownshiftApp).host

    private val _config = MutableStateFlow(LabConfig())
    val config: StateFlow<LabConfig> = _config.asStateFlow()
    private val _live = MutableStateFlow(LabLive())
    val live: StateFlow<LabLive> = _live.asStateFlow()

    init {
        // Publishes only while the lab is on screen: it is one tab among several now.
        viewModelScope.launch {
            combine(_config.subscriptionCount, _live.subscriptionCount) { a, b -> a + b > 0 }.distinctUntilChanged().collectLatest { shown ->
                while (shown) {
                    publish()
                    delay(UI_MS)
                }
            }
        }
    }

    fun start() = host.start()

    fun stop() = host.stop()

    fun setBuild(b: Build) {
        host.setBuild(b)
        publish()
    }

    /** null hands the pedal back to the console or the estimate. */
    fun setManualThrottle(value: Double?) = host.setManualThrottle(value)

    fun record(on: Boolean, name: String = "lab") {
        host.record(on, name)
        publish()
    }

    private fun publish() {
        _config.value = LabConfig(
            ready = host.ready,
            build = host.build,
            packSource = host.packSource,
            consoleConnected = host.consoleConnected,
            fuel = host.fuel,
            audioInfo = host.audioInfo,
            recording = host.recording,
        )
        val lat = host.latency
        fun f(v: Double?) = v?.let { "%.0f".format(it) } ?: "-"
        // Rounded to what the gauges show, so a steady engine publishes nothing new and nothing redraws.
        val t = host.telemetry
        val inp = host.lastInputs
        _live.value = LabLive(
            telemetry = t.copy(rpm = round(t.rpm / 10) * 10, load = round(t.load * 100) / 100, boost = round(t.boost * 100) / 100),
            speedKph = round(inp.speedMps * 3.6),
            throttle = round(inp.throttle * 100) / 100,
            source = host.lastSource,
            manual = host.manual != null,
            latency = if (lat == null || lat.count == 0) "" else "페달→소리 p50 ${f(lat.p50())} / p95 ${f(lat.p95())} ms (n=${lat.count})",
        )
    }

    private companion object {
        // Each frame of this screen costs about 25 ms of CPU on the emulator (software rendering), so 5 Hz, not 10.
        const val UI_MS = 200L
    }
}
