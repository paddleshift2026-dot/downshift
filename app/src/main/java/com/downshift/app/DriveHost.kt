package com.downshift.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.downshift.app.adapters.audio.GuidanceTone
import com.downshift.app.adapters.audio.PlaybackWatcher
import com.downshift.app.adapters.demo.ConsoleCommand
import com.downshift.app.adapters.demo.DemoBus
import com.downshift.app.adapters.demo.DemoCommand
import com.downshift.app.adapters.pleos.NaviEvents
import com.downshift.app.adapters.pleos.PleosVehicle
import com.downshift.app.adapters.store.GarageRepository
import com.downshift.app.core.flow.Screen
import com.downshift.app.core.flow.ScreenFlow
import com.downshift.app.core.garage.Catalog
import com.downshift.app.core.garage.Garage
import com.downshift.app.core.garage.Part
import com.downshift.app.core.model.GearState
import com.downshift.app.core.situation.Reason
import com.downshift.app.core.situation.Situation
import com.downshift.app.core.situation.SituationInputs
import com.downshift.app.core.situation.SoundSituation
import com.downshift.app.core.trip.DriveGauge
import com.downshift.app.core.trip.PlaceAhead
import com.downshift.app.core.trip.PlaceKind
import com.downshift.app.core.trip.PointRules
import com.downshift.app.core.trip.Side
import com.downshift.app.core.trip.SummaryView
import com.downshift.app.core.trip.TripCheckpoint
import com.downshift.app.core.trip.TripMeter
import com.downshift.app.core.trip.TripTick
import com.downshift.app.core.trip.TripTotals
import com.downshift.app.core.trip.TripView
import com.downshift.app.core.trip.TurnAhead
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

/** Bump when the safety features change, so the first-run note is agreed to again (PRD feature F). */
const val CONSENT_VERSION = 1

/** What the drive screen shows; published only when it changes. */
data class DriveLive(val gauge: DriveGauge, val situation: Situation, val muted: Boolean, val lapse: Int)

/**
 * The app's trips (PRD features C, D and E), one per process like EngineHost. A drive thread ticks every TICK_MS while
 * the engine runs, a trip is on or the car is out of park (every IDLE_TICK_MS otherwise, to notice the gear). Each tick
 * applies what the navigation, the car and adb sent; starts a trip when the gear leaves park and settles it into the
 * garage when it comes back; steps the trip; works out the situation volume for the engine; and every CHECKPOINT_MS
 * saves the trip, so a restarted app carries it on (out of park) or settles it once (in park). The Pleos Vehicle SDK
 * runs only while the engine or a trip needs it (it costs the main thread about 7% of a core, M1). The screen flow
 * also lives on the drive thread; the screens read screen, live and summary and call the rest from the main thread.
 */
class DriveHost(private val app: Application, private val engine: EngineHost, private val garage: GarageRepository) {
    private val tag = "DownshiftLab"
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Executors.newSingleThreadExecutor { r -> Thread(r, "DownshiftDrive").apply { isDaemon = true } }.asCoroutineDispatcher())
    private val events = ConcurrentLinkedQueue<DriveEvent>()
    private val pleos = PleosVehicle(app, invertSeats = { engine.consoleConnected }) { km -> events += DriveEvent.Odometer(km) }
    private val navi = NaviEvents(app) { events += it }
    private val watcher = PlaybackWatcher(app)

    @Volatile private var consented = garage.consentVersion >= CONSENT_VERSION
    @Volatile private var muted = false
    @Volatile private var pleosAllowed = true
    private var demoSeq = 0 // main thread

    // Drive thread only.
    private val flow = ScreenFlow(consented)
    private val situation = SoundSituation()
    private var trip: Trip? = null
    private var naviLimitKph = 0
    private var demoLimitKph = 0
    private var quiet = false
    private var lapse = 1
    private var lastTickNs = 0L
    private var pleosOn = false
    private var unneededSinceMs = 0L

    private val _screen = MutableStateFlow(flow.screen)
    val screen: StateFlow<Screen> = _screen.asStateFlow()
    private val _live = MutableStateFlow(DriveLive(TripView.gauge(null, 0, 0), Situation(1.0, Reason.OFF, 0), false, 1))
    val live: StateFlow<DriveLive> = _live.asStateFlow()
    private val _summary = MutableStateFlow<SummaryView?>(null)
    val summary: StateFlow<SummaryView?> = _summary.asStateFlow()

    /** One trip: when it began, the part its ring fills toward and the balance it fills from. */
    private class Trip(val startedAtMs: Long, val target: Part?, val balanceAtStart: Int, val meter: TripMeter, var savedAtMs: Long)

    init {
        if (consented) listen()
        scope.launch {
            resume()
            while (true) delay(if (tick()) TICK_MS else IDLE_TICK_MS)
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch { DemoBus.commands.collect { handle(it) } }
    }

    fun agree() = onDrive {
        garage.consentVersion = CONSENT_VERSION
        if (!consented) main.post { listen() }
        consented = true
        flow.agree()
    }

    fun reviewSafety() = onDrive { flow.reviewSafety() }

    fun openStart() = onDrive { flow.openStart() }

    fun back() = onDrive { flow.back() }

    fun setMuted(on: Boolean) {
        muted = on
    }

    fun demoGear(drive: Boolean) = engine.demo(ConsoleCommand.Gear(if (drive) GearState.DRIVE else GearState.PARK))

    fun demoArrive() = engine.demo(ConsoleCommand.Arrive)

    /** The demo pedal button: on the emulator the console pedal outranks a manual one, so it presses that. */
    fun demoPedal(down: Boolean) {
        if (engine.consoleConnected) {
            engine.demo(ConsoleCommand.Accelerator(if (down) DEMO_PEDAL_PCT else 0))
        } else {
            engine.setManualThrottle(if (down) DEMO_PEDAL_PCT / 100.0 else null)
        }
    }

    private fun onDrive(change: () -> Unit) {
        scope.launch {
            change()
            _screen.value = flow.screen
        }
    }

    private fun listen() {
        navi.start()
        watcher.start()
    }

    /** A trip saved by an app that was stopped: carried on when the car is still out of park, otherwise settled. */
    private suspend fun resume() {
        val c = garage.loadTrip() ?: return
        var waited = 0L
        while (engine.gear == GearState.UNKNOWN && waited < GEAR_WAIT_MS) {
            delay(100)
            waited += 100
        }
        val g = engine.gear
        val t = Trip(c.startedAtMs, c.targetId?.let(Catalog::part), garage.state.value.balance, TripMeter(c.totals), System.currentTimeMillis())
        trip = t
        flow.tripStarted()
        _screen.value = flow.screen
        if (g == GearState.DRIVE || g == GearState.REVERSE || g == GearState.NEUTRAL) {
            Log.i(tag, "trip resumed km=${fmt(c.totals.distanceKm)} gear=$g")
            main.post { pleos.readOdometer() }
        } else {
            Log.i(tag, "trip settled after a restart km=${fmt(c.totals.distanceKm)} gear=$g")
            finishTrip()
        }
    }

    /** One drive-thread tick; true while it should come again soon. */
    private fun tick(): Boolean {
        val nowNs = System.nanoTime()
        val dt = if (lastTickNs == 0L) 0.0 else ((nowNs - lastTickNs) / 1e9).coerceAtMost(MAX_DT_SEC)
        lastTickNs = nowNs
        drainEvents()
        val gear = engine.gear
        val out = gear == GearState.DRIVE || gear == GearState.REVERSE
        if (trip == null && out && consented) startTrip()
        if (trip != null && gear == GearState.PARK) finishTrip()
        val now = System.currentTimeMillis()
        trip?.let { t ->
            val limit = if (demoLimitKph > 0) demoLimitKph else naviLimitKph
            t.meter.step(TripTick(engine.speedMps, pleos.signal, limit), dt * lapse)
            if (now - t.savedAtMs >= CHECKPOINT_MS) {
                garage.saveTrip(TripCheckpoint(startedAtMs = t.startedAtMs, savedAtMs = now, targetId = t.target?.id, totals = t.meter.totals()))
                t.savedAtMs = now
            }
        }
        val running = engine.telemetry.running
        val inputs = SituationInputs(running, muted, watcher.priority, trip?.meter?.schoolZone ?: false, quiet, pleos.passenger, engine.build.volume)
        val s = situation.step(inputs, dt)
        engine.setSituationGain(s.gain)
        publish(s)
        vehicleSignals(running || trip != null, now)
        return running || trip != null || (gear != GearState.PARK && gear != GearState.UNKNOWN)
    }

    private fun drainEvents() {
        while (true) {
            when (val e = events.poll() ?: return) {
                is DriveEvent.Limit -> if (e.demo) demoLimitKph = e.kph.coerceAtLeast(0) else naviLimitKph = e.kph.coerceAtLeast(0)
                is DriveEvent.Turn -> trip?.meter?.turn(e.turn)
                is DriveEvent.Place -> trip?.meter?.place(e.place)
                is DriveEvent.Odometer -> if (lapse == 1) trip?.meter?.odometer(e.km)
                is DriveEvent.Quiet -> quiet = e.on
                is DriveEvent.Lapse -> lapse = e.factor.coerceIn(1, MAX_LAPSE)
            }
        }
    }

    private fun startTrip() {
        val s = garage.state.value
        val target = Garage.nextTarget(s)
        val now = System.currentTimeMillis()
        trip = Trip(now, target, s.balance, TripMeter(), now)
        main.post { pleos.readOdometer() }
        Log.i(tag, "trip start target=${target?.id} balance=${s.balance}")
        flow.tripStarted()
        _screen.value = flow.screen
    }

    private fun finishTrip() {
        val t = trip ?: return
        trip = null
        val totals: TripTotals = t.meter.finish()
        val points = PointRules.of(totals)
        val after = garage.finishTrip(points.total)
        _summary.value = TripView.summary(after, totals, points)
        Log.i(
            tag,
            "trip done km=${fmt(totals.distanceKm)} known=${fmt(totals.knownKm)} kept=${fmt(totals.keptKm)} unknown=${fmt(totals.unknownKm)} " +
                "places=${totals.placesKept}/${totals.placesPassed} turns=${totals.signalled}/${totals.turns} " +
                "points=${points.distance}+${points.limit}+${points.places}+${points.signals}=${points.total} balance=${after.balance} lapse=$lapse",
        )
        flow.tripEnded(PointRules.showsSummary(totals))
        _screen.value = flow.screen
    }

    private fun publish(s: Situation) {
        val prev = _live.value
        if (s.reason != prev.situation.reason) Log.i(tag, "situation ${s.reason} ${s.volumePct}%")
        val t = trip
        val gauge = if (t != null) {
            TripView.gauge(t.target, t.balanceAtStart, PointRules.of(t.meter.totals()).total)
        } else {
            val g = garage.state.value
            TripView.gauge(Garage.nextTarget(g), g.balance, 0)
        }
        val next = DriveLive(gauge, s, muted, lapse)
        if (next != prev) _live.value = next
    }

    /** The Pleos Vehicle SDK on while the engine or a trip needs it; off PLEOS_LINGER_MS after neither does. */
    private fun vehicleSignals(needed: Boolean, now: Long) {
        val want = needed && pleosAllowed
        if (want) {
            unneededSinceMs = 0L
            if (!pleosOn) {
                pleosOn = true
                main.post { pleos.start() }
            }
            return
        }
        if (!pleosOn) return
        if (unneededSinceMs == 0L) unneededSinceMs = now
        if (!pleosAllowed || now - unneededSinceMs >= PLEOS_LINGER_MS) {
            pleosOn = false
            main.post { pleos.stop() }
        }
    }

    /** adb: what the emulator's navigation cannot send, and the flow's buttons (see DemoReceiver). */
    private fun handle(c: DemoCommand) {
        if (c !is DemoCommand.Drive) return
        when (c.event) {
            "agree" -> agree()
            "screen" -> if (c.to == "start") openStart() else back()
            "limit" -> events += DriveEvent.Limit(c.kph ?: 0, demo = true)
            "camera" -> events += DriveEvent.Place(PlaceAhead(demoId("camera"), PlaceKind.CAMERA, c.kph ?: 50, (c.m ?: 300).toDouble()))
            "section" -> {
                val start = (c.m ?: 100).toDouble()
                events += DriveEvent.Place(PlaceAhead(demoId("section-start"), PlaceKind.SECTION_START, c.kph ?: 80, start))
                events += DriveEvent.Place(PlaceAhead(demoId("section-end"), PlaceKind.SECTION_END, c.kph ?: 80, start + (c.len ?: 1000)))
            }
            "school" -> events += DriveEvent.Place(PlaceAhead(demoId("school"), PlaceKind.SCHOOL_ZONE, c.kph ?: 0, (c.m ?: 150).toDouble()))
            "turn" -> events += DriveEvent.Turn(TurnAhead(if (c.dir == "right") Side.RIGHT else Side.LEFT, c.road == "highway", (c.m ?: 120).toDouble()))
            "quiet" -> events += DriveEvent.Quiet(c.value == "on")
            "guide" -> GuidanceTone.guidance(app)
            "music" -> GuidanceTone.music(app)
            "mute" -> setMuted(c.value == "on")
            "lapse" -> events += DriveEvent.Lapse(c.value?.toIntOrNull() ?: 1)
            "pleos" -> pleosAllowed = c.value != "off"
            "route" -> if (c.value == "cancel") navi.cancelRoute()
            else -> Log.w(tag, "unknown drive event ${c.event}")
        }
        Log.i(tag, "drive event $c")
    }

    private fun demoId(kind: String) = "demo-$kind-${++demoSeq}"

    private fun fmt(km: Double) = String.format(Locale.ROOT, "%.2f", km)

    private companion object {
        const val TICK_MS = 100L
        const val IDLE_TICK_MS = 500L
        const val MAX_DT_SEC = 0.5 // a stalled tick must not jump the trip
        const val CHECKPOINT_MS = 10_000L
        const val GEAR_WAIT_MS = 3_000L
        const val PLEOS_LINGER_MS = 5_000L
        const val DEMO_PEDAL_PCT = 85
        const val MAX_LAPSE = 50
    }
}
