package com.downshift.app

import android.app.Application
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.downshift.app.adapters.audio.EngineAudioOutput
import com.downshift.app.adapters.car.CarSignalsAdapter
import com.downshift.app.adapters.demo.ConsoleDrive
import com.downshift.app.adapters.demo.DemoBus
import com.downshift.app.adapters.demo.DemoCommand
import com.downshift.app.adapters.pack.AssetPackLoader
import com.downshift.app.core.audio.LatencyStats
import com.downshift.app.core.build.Build
import com.downshift.app.core.build.EngineId
import com.downshift.app.core.engine.EngineSpec
import com.downshift.app.core.engine.Engines
import com.downshift.app.core.garage.Audition
import com.downshift.app.core.garage.Category
import com.downshift.app.core.input.InputArbiter
import com.downshift.app.core.input.ThrottleSource
import com.downshift.app.core.model.DriveInputs
import com.downshift.app.core.model.FuelKind
import com.downshift.app.core.model.GearState
import com.downshift.app.core.pack.LoadedPack
import com.downshift.app.core.pack.ProceduralPack
import com.downshift.app.core.powertrain.LoadEstimator
import com.downshift.app.core.runtime.EngineRuntime
import com.downshift.app.core.runtime.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The app's one engine: car signals, the console pedal with demo physics, the input priority, the speed-based load
 * estimate, the pack choice, the runtime and its audio output. Every screen and adb (DemoBus) drive this same engine.
 *
 * A preview (audition) plays a short scripted drive with a given build instead of the real inputs, starting the engine
 * for it when it was off, and afterwards puts back the driver's build and running state. Any other command cancels a
 * preview first. Call everything from the main thread; the audio thread only calls inputs().
 */
class EngineHost(private val app: Application) {
    private val tag = "DownshiftLab"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private val arbiter = InputArbiter()
    private val estimator = LoadEstimator()
    @Volatile private var estimate = 0.0
    @Volatile private var lastSpeedNs = 0L
    @Volatile private var lastPedalAtMs = 0L
    private val car = CarSignalsAdapter(app) { mps, atNs -> onSpeed(mps, atNs) }
    // A preview plays its own script, so the pedals only need fast polling while the engine runs outside one.
    private val console = ConsoleDrive(gear = { car.gear }, fast = { telemetry.running && preview == null }) { p ->
        arbiter.console(p.throttle, p.brake, p.atMs)
        lastPedalAtMs = p.atMs
    }
    private val packs = HashMap<EngineId, LoadedPack>()
    private var runtime: EngineRuntime? = null
    private var output: EngineAudioOutput? = null
    private var wanted = Build() // what the runtime should play: the preview's build during a preview
    private var startedForPreview = false
    @Volatile private var preview: Preview? = null

    /** The driver's build, not the preview's. */
    var build = Build()
        private set
    /** The driver's start and stop; a preview may run the engine while this stays off. */
    var engineOn = false
        private set
    @Volatile var packSource = ""
        private set
    var manual: Double? = null
        private set
    var recording: String? = null
        private set
    @Volatile var lastInputs = DriveInputs(0.0, 0.0, 0.0, GearState.UNKNOWN)
        private set
    @Volatile var lastSource = ThrottleSource.ESTIMATE
        private set

    val ready: Boolean get() = runtime != null
    val telemetry: Telemetry get() = runtime?.telemetry ?: Telemetry.OFF
    val consoleConnected: Boolean get() = console.connected
    val fuel: FuelKind get() = car.fuel
    val audioInfo: String get() = output?.bufferInfo ?: ""
    val latency: LatencyStats? get() = output?.latency

    /** In park, neutral or an unknown gear and below 1 km/h (the demo's own speed included): the garage may change things. */
    val parked: Boolean
        get() {
            val g = car.gear
            return (g == GearState.PARK || g == GearState.NEUTRAL || g == GearState.UNKNOWN) && speedMps() < PARKED_MPS
        }

    init {
        car.start()
        console.start()
        scope.launch {
            val first = build
            val pack = withContext(Dispatchers.Default) { packFor(first.engine) }
            packSource = pack.source
            val rate = app.getSystemService(AudioManager::class.java).getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48_000
            val rt = EngineRuntime(pack, first, car.fuel, rate)
            runtime = rt
            output = EngineAudioOutput(app, rt, ::inputs) { if (preview != null) 0L else lastPedalAtMs }.also { it.start() }
            Log.i(tag, "engine ready: ${first.engine} pack=${pack.source} fuel=${car.fuel} rate=$rate")
            if (wanted != first) play(wanted) // a build set while the runtime was being made
        }
        scope.launch { DemoBus.commands.collect { handle(it) } }
    }

    fun start() {
        cancelAudition()
        engineOn = true
        runtime?.start()
        // tools/sound.sh latency reads the buffer length from this line.
        output?.let { Log.i("DownshiftAudio", "AudioTrack ${it.bufferInfo}") }
    }

    fun stop() {
        cancelAudition()
        engineOn = false
        runtime?.stop()
    }

    fun setBuild(b: Build) {
        cancelAudition()
        val limit = EngineSpec.MAX_REDLINE_OFFSET
        build = b.copy(pops = b.pops.coerceIn(0.0, 1.0), volume = b.volume.coerceIn(0.0, 1.0), redlineOffset = b.redlineOffset.coerceIn(-limit, limit))
        play(build)
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
            out.stopRecording(File(app.filesDir, "$n.wav"))
        }
    }

    /**
     * Plays [category]'s scripted drive with [build] fitted, when parked; a preview already playing is replaced.
     * [onDone] runs on the main thread when this preview ends or is cancelled (not when another one replaces it),
     * and straight away when it cannot play.
     */
    fun audition(build: Build, category: Category, onDone: () -> Unit) {
        val rt = runtime
        if (rt == null || !parked) {
            onDone()
            return
        }
        preview?.ended?.set(true)
        val cold = !engineOn && !startedForPreview
        val p = Preview(Audition(category, cold), onDone)
        preview = p
        wanted = build
        scope.launch {
            val pack = withContext(Dispatchers.Default) { packFor(build.engine) }
            if (preview !== p) return@launch
            packSource = pack.source
            rt.setBuild(build, pack)
            if (cold) {
                rt.start()
                startedForPreview = true
            }
            p.live = true
            Log.i(tag, "preview $category $build cold=$cold")
        }
    }

    /** Stops a preview early and puts the driver's build and running state back. */
    fun cancelAudition() {
        val p = preview ?: return
        p.ended.set(true)
        preview = null
        restore()
        p.onDone()
    }

    private fun finish(p: Preview) {
        if (preview !== p) return
        preview = null
        restore()
        p.onDone()
    }

    private fun restore() {
        if (startedForPreview && !engineOn) runtime?.stop()
        startedForPreview = false
        play(build)
    }

    private fun play(b: Build) {
        wanted = b
        scope.launch {
            val pack = withContext(Dispatchers.Default) { packFor(b.engine) }
            if (wanted != b) return@launch // a newer change is on its way
            packSource = pack.source
            runtime?.setBuild(b, pack)
            Log.i(tag, "build $b pack=${pack.source}")
        }
    }

    /** Called on the audio thread once per block; a live preview's script stands in for the pedals, speed and gear. */
    private fun inputs(blockSec: Double): DriveInputs {
        val p = preview
        if (p != null && p.live && !p.ended.get()) {
            if (p.t <= p.script.durationSec && parked) {
                val inp = p.script.inputs(p.t)
                p.t += blockSec
                lastInputs = inp
                return inp
            }
            // The script is over or the car moved off: back to the real inputs, and the main thread tidies up.
            if (p.ended.compareAndSet(false, true)) main.post { finish(p) }
        }
        arbiter.vehicleBrake(car.brakePressed)
        val pick = arbiter.pick(System.currentTimeMillis(), estimate)
        lastSource = pick.source
        return DriveInputs(pick.throttle, pick.brake, speedMps(), car.gear).also { lastInputs = it }
    }

    // In the demo the app computes the speed itself (50 Hz, continuous); the AAOS copy comes back in whole km/h
    // about ten times a second, so it only drives the cluster. On a real car AAOS is the only source.
    private fun speedMps() = if (console.connected && console.demoPhysics) console.demoSpeedMps else car.speedMps

    private fun onSpeed(mps: Double, atNs: Long) {
        val prev = lastSpeedNs
        lastSpeedNs = atNs
        if (prev != 0L) estimate = estimator.update(mps, (atNs - prev) / 1e9)
    }

    private fun packFor(engine: EngineId): LoadedPack = synchronized(packs) {
        // Licensed recordings when they are installed, otherwise the engine is synthesised in real time.
        packs.getOrPut(engine) { AssetPackLoader.load(app, engine) ?: ProceduralPack.build(Engines.spec(engine)) }
    }

    private fun handle(c: DemoCommand) {
        when (c) {
            is DemoCommand.Throttle -> setManualThrottle(c.value)
            DemoCommand.EngineStart -> start()
            DemoCommand.EngineStop -> stop()
            is DemoCommand.BuildChange -> setBuild(c.applyTo(build))
            is DemoCommand.Record -> record(c.on, c.name)
            else -> Unit // probe and garage commands
        }
    }

    /** One preview: the script, and how far the audio thread has played it. */
    private class Preview(val script: Audition, val onDone: () -> Unit) {
        var t = 0.0 // audio thread only
        @Volatile var live = false // the preview's build has been handed to the runtime
        val ended = AtomicBoolean(false)
    }

    private companion object {
        const val PARKED_MPS = 1.0 / 3.6
        const val MAX_RECORD_SEC = 60
    }
}
