package com.downshift.app.ui

import android.app.Application
import android.media.AudioManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.downshift.app.adapters.audio.EngineAudioOutput
import com.downshift.app.adapters.car.CarSignalsAdapter
import com.downshift.app.adapters.demo.ConsoleDrive
import com.downshift.app.adapters.demo.DemoBus
import com.downshift.app.adapters.demo.DemoCommand
import com.downshift.app.adapters.pack.AssetPackLoader
import com.downshift.app.core.build.Build
import com.downshift.app.core.build.EngineId
import com.downshift.app.core.engine.Engines
import com.downshift.app.core.input.InputArbiter
import com.downshift.app.core.input.ThrottleSource
import com.downshift.app.core.model.DriveInputs
import com.downshift.app.core.model.FuelKind
import com.downshift.app.core.model.GearState
import com.downshift.app.core.pack.LoadedPack
import com.downshift.app.core.pack.SynthPack
import com.downshift.app.core.powertrain.LoadEstimator
import com.downshift.app.core.runtime.EngineRuntime
import com.downshift.app.core.runtime.Telemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
 * Wires the engine together for the sound lab: car signals, the console pedal with demo physics, the input priority,
 * the speed-based load estimate, the runtime and its audio output. The screen and adb (DemoBus) call the same functions.
 */
class SoundLabViewModel(app: Application) : AndroidViewModel(app) {
    private val tag = "DownshiftLab"
    private val arbiter = InputArbiter()
    private val estimator = LoadEstimator()
    @Volatile private var estimate = 0.0
    @Volatile private var lastSpeedNs = 0L
    @Volatile private var lastPedalAtMs = 0L
    @Volatile private var lastInputs = DriveInputs(0.0, 0.0, 0.0, GearState.UNKNOWN)
    @Volatile private var lastSource = ThrottleSource.ESTIMATE
    private val car = CarSignalsAdapter(app) { mps, atNs -> onSpeed(mps, atNs) }
    private val console = ConsoleDrive(gear = { car.gear }, fast = { runtime?.telemetry?.running == true }) { p ->
        arbiter.console(p.throttle, p.brake, p.atMs)
        lastPedalAtMs = p.atMs
    }
    private val packs = HashMap<EngineId, LoadedPack>()
    private var runtime: EngineRuntime? = null
    private var output: EngineAudioOutput? = null
    private var build = Build()
    private var packSource = ""
    private var manual: Double? = null
    private var recording: String? = null

    private val _config = MutableStateFlow(LabConfig())
    val config: StateFlow<LabConfig> = _config.asStateFlow()
    private val _live = MutableStateFlow(LabLive())
    val live: StateFlow<LabLive> = _live.asStateFlow()

    init {
        car.start()
        console.start()
        viewModelScope.launch {
            val pack = withContext(Dispatchers.Default) { packFor(build.engine) }
            packSource = pack.source
            val rate = app.getSystemService(AudioManager::class.java).getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48_000
            val rt = EngineRuntime(pack, build, car.fuel, rate)
            runtime = rt
            output = EngineAudioOutput(app, rt, ::inputs) { lastPedalAtMs }.also { it.start() }
            Log.i(tag, "sound lab ready: ${build.engine} pack=${pack.source} fuel=${car.fuel} rate=$rate")
            while (isActive) {
                publish()
                delay(UI_MS)
            }
        }
        viewModelScope.launch { DemoBus.commands.collect { handle(it) } }
    }

    fun start() {
        runtime?.start()
        // tools/sound.sh latency reads the buffer length from this line.
        output?.let { Log.i("DownshiftAudio", "AudioTrack ${it.bufferInfo}") }
    }

    fun stop() = runtime?.stop()

    fun setBuild(b: Build) {
        val next = b.copy(pops = b.pops.coerceIn(0.0, 1.0), volume = b.volume.coerceIn(0.0, 1.0), redlineOffset = b.redlineOffset.coerceIn(-500, 500))
        build = next
        publish()
        viewModelScope.launch {
            val pack = withContext(Dispatchers.Default) { packFor(next.engine) }
            if (build != next) return@launch // a newer change is on its way
            packSource = pack.source
            runtime?.setBuild(next, pack)
            Log.i(tag, "build $next pack=${pack.source}")
        }
    }

    /** null hands the pedal back to the console or the estimate. */
    fun setManualThrottle(value: Double?) {
        manual = value
        arbiter.manual(value)
    }

    fun record(on: Boolean, name: String = "lab") {
        val out = output ?: return
        if (on) {
            out.startRecording(MAX_RECORD_SEC)
            recording = name
        } else {
            val n = recording ?: name
            recording = null
            out.stopRecording(File(getApplication<Application>().filesDir, "$n.wav"))
        }
        publish()
    }

    /** Called on the audio thread once per block. */
    private fun inputs(): DriveInputs {
        arbiter.vehicleBrake(car.brakePressed)
        val p = arbiter.pick(System.currentTimeMillis(), estimate)
        // In the demo the app computes the speed itself (50 Hz, continuous); the AAOS copy comes back in whole km/h
        // about ten times a second, so it only drives the cluster. On a real car AAOS is the only source.
        val speed = if (console.connected && console.demoPhysics) console.demoSpeedMps else car.speedMps
        lastSource = p.source
        return DriveInputs(p.throttle, p.brake, speed, car.gear).also { lastInputs = it }
    }

    private fun onSpeed(mps: Double, atNs: Long) {
        val prev = lastSpeedNs
        lastSpeedNs = atNs
        if (prev != 0L) estimate = estimator.update(mps, (atNs - prev) / 1e9)
    }

    private fun packFor(engine: EngineId): LoadedPack = synchronized(packs) {
        packs.getOrPut(engine) { AssetPackLoader.load(getApplication(), engine) ?: SynthPack.build(Engines.spec(engine)) }
    }

    private fun handle(c: DemoCommand) {
        when (c) {
            is DemoCommand.Throttle -> setManualThrottle(c.value)
            DemoCommand.EngineStart -> start()
            DemoCommand.EngineStop -> stop()
            is DemoCommand.BuildChange -> setBuild(c.applyTo(build))
            is DemoCommand.Record -> record(c.on, c.name)
            else -> Unit // probe commands
        }
    }

    private fun publish() {
        val rt = runtime
        val out = output
        _config.value = LabConfig(
            ready = rt != null,
            build = build,
            packSource = packSource,
            consoleConnected = console.connected,
            fuel = car.fuel,
            audioInfo = out?.bufferInfo ?: "",
            recording = recording,
        )
        val lat = out?.latency
        fun f(v: Double?) = v?.let { "%.0f".format(it) } ?: "-"
        _live.value = LabLive(
            telemetry = rt?.telemetry ?: Telemetry.OFF,
            speedKph = lastInputs.speedMps * 3.6,
            throttle = lastInputs.throttle,
            source = lastSource,
            manual = manual != null,
            latency = if (lat == null || lat.count == 0) "" else "페달→소리 p50 ${f(lat.p50())} / p95 ${f(lat.p95())} ms (n=${lat.count})",
        )
    }

    override fun onCleared() {
        output?.stop()
        console.stop()
        car.stop()
    }

    private companion object {
        // Each frame of this screen costs about 25 ms of CPU on the emulator (software rendering), so 5 Hz, not 10.
        const val UI_MS = 200L
        const val MAX_RECORD_SEC = 60
    }
}
