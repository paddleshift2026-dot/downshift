package com.downshift.app.ui.start

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.downshift.app.DownshiftApp
import com.downshift.app.core.engine.Engines
import com.downshift.app.core.garage.Catalog
import com.downshift.app.core.garage.Garage
import com.downshift.app.core.garage.GarageState
import com.downshift.app.core.garage.Turntable
import com.downshift.app.ui.garage.engineLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * What changes rarely on the start screen: the engine's state, whether it may start (consented, the speed permission,
 * the sound ready), the build's plate and name, and the dial's scale. fullScale is 8,000 rpm, or 9,000 when the
 * redline (with the tune's offset) is past 7,500, so the red zone always shows.
 */
data class StartUi(
    val on: Boolean = false,
    val ready: Boolean = false,
    val permitted: Boolean = true,
    val failed: Boolean = false,
    val revving: Boolean = false,
    val plate: String = "",
    val engineLine: String = "",
    val variant: String = "i4",
    val fullScale: Int = 8000,
    val redline: Float = 0.85f,
)

/**
 * The start screen (design: Start.dc.html): START held for 0.9 s starts the fitted build, STOP stops it, and the demo
 * buttons press the console pedal and shift into D. The engine is polled ten times a second, only while the screen is
 * shown; the rpm is a separate flow read while drawing.
 */
class StartViewModel(app: Application) : AndroidViewModel(app) {
    private val host = (app as DownshiftApp).host
    private val drive = (app as DownshiftApp).drive
    private val garage = (app as DownshiftApp).garage
    private val openedAtMs = System.currentTimeMillis()

    private val _ui = MutableStateFlow(StartUi())
    val ui: StateFlow<StartUi> = _ui.asStateFlow()
    private val _rpm = MutableStateFlow(0)
    /** The engine's rpm to the nearest 10, 0 when it is off. */
    val rpm: StateFlow<Int> = _rpm.asStateFlow()
    private val _ignitions = MutableStateFlow(0)
    /** Counts starts made here, so the dial sweeps once per start. */
    val ignitions: StateFlow<Int> = _ignitions.asStateFlow()

    init {
        viewModelScope.launch { garage.state.collect { s -> _ui.update { describe(it, s) } } }
        viewModelScope.launch {
            _ui.subscriptionCount.map { it > 0 }.distinctUntilChanged().collectLatest { shown ->
                while (shown) {
                    poll()
                    delay(POLL_MS)
                }
            }
        }
    }

    fun start() {
        val u = _ui.value
        if (!u.permitted || !host.ready || !host.parked) return
        host.start()
        _ignitions.update { it + 1 }
    }

    fun stop() = host.stop()

    fun back() = drive.back()

    fun demoPedal(down: Boolean) = drive.demoPedal(down)

    fun demoDrive() = drive.demoGear(true)

    /** Demo mode (the header's "시연") and whether its dashed buttons are shown. */
    val demoOn: StateFlow<Boolean> = (app as DownshiftApp).demo.on
    val demoButtons: StateFlow<Boolean> = (app as DownshiftApp).demo.buttons

    private fun poll() {
        val t = host.telemetry
        _rpm.value = if (t.running) (t.rpm / 10).roundToInt() * 10 else 0
        val permitted = ContextCompat.checkSelfPermission(getApplication(), SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED
        val failed = !host.ready && System.currentTimeMillis() - openedAtMs > READY_WAIT_MS
        _ui.update {
            it.copy(on = host.engineOn, ready = host.ready, permitted = permitted, failed = failed, revving = t.running && host.lastInputs.throttle >= REV_THROTTLE)
        }
    }

    private fun describe(u: StartUi, s: GarageState): StartUi {
        val engine = Catalog.part(s.engine)!!
        val spec = Engines.spec(Garage.build(s).engine)
        val extra = when (Turntable.variant(s.engine, s.intake)) {
            "v8sc" -> " · 슈퍼차저"
            "v12t" -> " · 트윈 터보"
            else -> ""
        }
        val redline = spec.redlineRpm + s.tune.redlineOffset
        val full = if (redline > 7_500) 9_000 else 8_000
        return u.copy(
            plate = Garage.plate(Garage.build(s)),
            engineLine = "${engineLabel(engine)} · 점화 순서 ${spec.firingOrder}$extra",
            variant = Turntable.variant(s.engine, s.intake),
            fullScale = full,
            redline = (redline / full).toFloat(),
        )
    }

    private companion object {
        const val POLL_MS = 100L
        const val READY_WAIT_MS = 5_000L
        const val REV_THROTTLE = 0.3
        const val SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
    }
}
