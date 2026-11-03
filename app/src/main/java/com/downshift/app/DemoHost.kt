package com.downshift.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.downshift.app.adapters.demo.DemoBus
import com.downshift.app.adapters.demo.DemoCommand
import com.downshift.app.adapters.demo.PedalServer
import com.downshift.app.adapters.demo.PedalStatus
import com.downshift.app.adapters.store.GarageRepository
import com.downshift.app.core.demo.DemoPolicy
import com.downshift.app.core.demo.PageState
import com.downshift.app.core.garage.Garage
import com.downshift.app.core.garage.GarageState
import com.downshift.app.core.model.GearState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Demo mode (PRD feature G), one per process like DriveHost: switching to the demo wallet and back, the demo wallet's
 * buttons (+1,000 P and the design's demo state), the distance lapse, adb's garage add and reset (which work with no
 * screen up), and the web pedal with the presenter's scenes, open only in demo mode. Demo mode cannot change while a
 * trip is being recorded, so a trip is never split between the wallets. Main thread.
 */
class DemoHost(private val app: Application, private val garage: GarageRepository, private val engine: EngineHost, private val drive: DriveHost) {
    private val tag = "DownshiftLab"
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var server: PedalServer? = null
    private var serverStatus: Job? = null

    /** Demo mode is on: the demo wallet is in use and the demo tools are there. */
    val on: StateFlow<Boolean> = garage.demo
    private val _buttons = MutableStateFlow(garage.demo.value && garage.demoButtons)
    /** The dashed demo buttons are shown: demo mode is on and the tune tab's switch says so. */
    val buttons: StateFlow<Boolean> = _buttons.asStateFlow()
    private val _pedal = MutableStateFlow(PedalStatus())
    /** The web pedal: running (demo mode), pages connected, the pedal pressed. */
    val pedal: StateFlow<PedalStatus> = _pedal.asStateFlow()

    init {
        scope.launch { DemoBus.commands.collect { handle(it) } }
        if (garage.demo.value) startPedal()
    }

    /** Switches demo mode; false, with nothing changed, while a trip is being recorded. */
    fun setOn(on: Boolean): Boolean {
        if (on == garage.demo.value) return true
        if (drive.tripActive) {
            Log.i(tag, "demo switch refused: a trip is running")
            return false
        }
        engine.cancelAudition()
        val s = garage.setDemo(on)
        engine.setBuild(Garage.build(s))
        if (on) {
            startPedal()
        } else {
            stopPedal()
            drive.setLapse(1)
            drive.demoPedalPct(0)
        }
        _buttons.value = on && garage.demoButtons
        Log.i(tag, "demo ${if (on) "on" else "off"} balance=${s.balance}")
        return true
    }

    fun setButtons(show: Boolean) {
        garage.demoButtons = show
        _buttons.value = garage.demo.value && show
    }

    fun addPoints(n: Int = DEMO_POINTS) {
        if (!garage.demo.value) return
        garage.update { Garage.addPoints(it, n) }
    }

    /** The demo wallet back to the design's demo state, fitted on the engine. */
    fun reset() {
        if (!garage.demo.value) return
        engine.cancelAudition()
        garage.update { GarageState.DEMO }
        engine.setBuild(Garage.build(GarageState.DEMO))
    }

    fun setLapse(x: Int) {
        if (garage.demo.value) drive.setLapse(x)
    }

    /** The web page's presenter buttons, for the drive while the Pleos home covers the app (demo mode only). */
    fun scene(name: String) {
        if (!garage.demo.value) return
        when (name) {
            "drive" -> drive.demoGear(true)
            "arrive" -> drive.demoArrive()
            "school" -> DemoBus.post(DemoCommand.Drive("school", m = SCHOOL_AHEAD_M))
            "guide" -> DemoBus.post(DemoCommand.Drive("guide"))
            "passenger_on" -> drive.demoSeat(true)
            "passenger_off" -> drive.demoSeat(false)
        }
    }

    private fun startPedal() {
        if (server != null) return
        val s = PedalServer(app, onPedal = drive::demoPedalPct, onScene = { name -> main.post { scene(name) } }, state = ::pageState)
        server = s
        serverStatus = scope.launch { s.status.collect { _pedal.value = it } }
        s.startServer()
    }

    private fun stopPedal() {
        val s = server ?: return
        server = null
        s.stopServer()
        serverStatus?.cancel()
        _pedal.value = PedalStatus()
    }

    /** What the page shows, read on the server's timer: the engine, the car, the screen and the pedal. */
    private fun pageState(): PageState {
        val t = engine.telemetry
        val gear = when (engine.gear) {
            GearState.PARK -> "P"
            GearState.REVERSE -> "R"
            GearState.NEUTRAL -> "N"
            GearState.DRIVE -> "D"
            GearState.UNKNOWN -> "-"
        }
        return PageState(
            rpm = if (t.running) t.rpm.roundToInt() else 0,
            kph = (engine.speedMps * 3.6).roundToInt(),
            gear = gear,
            screen = drive.screen.value.name,
            engineOn = t.running,
            pct = _pedal.value.pct,
        )
    }

    private fun handle(c: DemoCommand) {
        when (c) {
            is DemoCommand.Demo -> {
                c.on?.let { setOn(it) }
                c.buttons?.let { setButtons(it) }
                c.lapse?.let { setLapse(it) }
            }
            is DemoCommand.GarageAction -> if (c.action in DemoPolicy.WALLET_ACTIONS) {
                when {
                    !DemoPolicy.allowsGarage(c.action, garage.demo.value) -> Log.i(tag, "demo mode is off: garage ${c.action} ignored")
                    c.action == "add" -> addPoints(c.points ?: DEMO_POINTS)
                    else -> reset()
                }
            }
            else -> Unit
        }
    }

    companion object {
        const val DEMO_POINTS = 1000
        private const val SCHOOL_AHEAD_M = 150
    }
}
