package com.downshift.app.ui.garage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.downshift.app.DownshiftApp
import com.downshift.app.adapters.demo.DemoBus
import com.downshift.app.adapters.demo.DemoCommand
import com.downshift.app.core.garage.Catalog
import com.downshift.app.core.garage.Category
import com.downshift.app.core.garage.Garage
import com.downshift.app.core.garage.GarageState
import com.downshift.app.core.garage.Part
import com.downshift.app.core.garage.PartKind
import com.downshift.app.core.garage.Tune
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

/** The garage tabs: one per part category, then the tune. */
enum class GarageTab(val category: Category?) {
    ENGINE(Category.ENGINE), INTAKE(Category.INTAKE), EXHAUST(Category.EXHAUST), TRANSMISSION(Category.TRANSMISSION), TUNE(null)
}

/**
 * What changes rarely: the saved garage, the tab, the card picked in each tab, the preview playing (a part id, or
 * [GarageViewModel.BUILD_PREVIEW]), and the engine's situation. equipSeq counts equips so the stage can flash the
 * slot of equipCategory once per equip.
 */
data class GarageUi(
    val state: GarageState = GarageState.FIRST_RUN,
    val tab: GarageTab = GarageTab.ENGINE,
    val selected: Map<Category, String> = fittedOf(GarageState.FIRST_RUN),
    val playing: String? = null,
    val parked: Boolean = true,
    val ready: Boolean = false,
    val engineOn: Boolean = false,
    val packSource: String = "",
    val equipSeq: Int = 0,
    val equipCategory: Category? = null,
) {
    /** The card picked in this tab, or null in the tune tab. */
    val selectedPart: Part? get() = tab.category?.let { c -> selected[c]?.let(Catalog::part) }
}

fun fittedOf(s: GarageState): Map<Category, String> = Category.entries.associateWith { Garage.fitted(s, it) }

/**
 * The garage's state and moves: pick parts, preview them (even locked ones), unlock with points, equip, tune, and the
 * demo buttons. Every change is saved at once and an equip or tune goes straight to the shared engine. Changes need
 * the car parked; the demo buttons always work. adb (DemoBus) reaches the same moves.
 */
class GarageViewModel(app: Application) : AndroidViewModel(app) {
    private val host = (app as DownshiftApp).host
    private val store = (app as DownshiftApp).garageStore

    private val _ui = MutableStateFlow(GarageUi())
    val ui: StateFlow<GarageUi> = _ui.asStateFlow()
    private val _rpm = MutableStateFlow(0)
    /** The engine's rpm to the nearest 10, 0 when it is off; read it while drawing. */
    val rpm: StateFlow<Int> = _rpm.asStateFlow()

    init {
        val s = store.load()
        _ui.value = GarageUi(state = s, selected = fittedOf(s))
        host.setBuild(Garage.build(s))
        // The engine's situation is polled five times a second, only while the garage is on screen; unchanged
        // values publish nothing.
        viewModelScope.launch {
            _ui.subscriptionCount.map { it > 0 }.distinctUntilChanged().collectLatest { shown ->
                while (shown) {
                    poll()
                    delay(POLL_MS)
                }
            }
        }
        viewModelScope.launch { DemoBus.commands.collect { handle(it) } }
    }

    fun selectTab(tab: GarageTab) {
        if (_ui.value.tab == tab) return
        host.cancelAudition()
        _ui.update { it.copy(tab = tab, playing = null) }
    }

    fun selectPart(id: String) {
        val p = Catalog.part(id) ?: return
        _ui.update { it.copy(selected = it.selected + (p.category to id)) }
    }

    /** Plays the part's preview (or the whole build's, for [BUILD_PREVIEW]), or stops it when it is playing. */
    fun preview(id: String) {
        val u = _ui.value
        if (u.playing == id) {
            host.cancelAudition()
            return
        }
        if (!host.parked) return
        val (build, category) = if (id == BUILD_PREVIEW) {
            Garage.build(u.state) to Category.ENGINE
        } else {
            val p = Catalog.part(id) ?: return
            if (p.kind != PartKind.NORMAL) return // nothing to hear yet
            Garage.preview(u.state, id) to p.category
        }
        _ui.update { it.copy(playing = id) }
        host.audition(build, category) { _ui.update { if (it.playing == id) it.copy(playing = null) else it } }
    }

    fun unlock(id: String) {
        if (!host.parked) return
        commit(Garage.unlock(_ui.value.state, id) ?: return)
    }

    fun equip(id: String) {
        if (!host.parked) return
        val next = Garage.equip(_ui.value.state, id) ?: return
        _ui.update { it.copy(equipSeq = it.equipSeq + 1, equipCategory = Catalog.part(id)?.category) }
        commit(next, toEngine = true)
    }

    fun setTune(t: Tune) {
        if (!host.parked) return
        commit(Garage.tune(_ui.value.state, t), toEngine = true)
    }

    fun start() = host.start()

    fun stop() = host.stop()

    fun demoAddPoints(points: Int = DEMO_POINTS) = commit(Garage.addPoints(_ui.value.state, points))

    /** The design's demo state, with the cards the PT walks through picked. */
    fun demoReset() {
        host.cancelAudition()
        _ui.update { it.copy(selected = DEMO_SELECTION, playing = null) }
        commit(GarageState.DEMO, toEngine = true)
    }

    private fun commit(s: GarageState, toEngine: Boolean = false) {
        _ui.update { it.copy(state = s) }
        store.save(s)
        if (toEngine) host.setBuild(Garage.build(s))
    }

    private fun poll() {
        val t = host.telemetry
        _rpm.value = if (t.running) (t.rpm / 10).roundToInt() * 10 else 0
        _ui.update { it.copy(parked = host.parked, ready = host.ready, engineOn = host.engineOn, packSource = host.packSource) }
    }

    private fun handle(c: DemoCommand) {
        if (c !is DemoCommand.GarageAction) return
        when (c.action) {
            "add" -> demoAddPoints(c.points ?: DEMO_POINTS)
            "reset" -> demoReset()
            "unlock", "equip", "preview" -> {
                val id = c.part ?: return
                selectPart(id)
                when (c.action) {
                    "unlock" -> unlock(id)
                    "equip" -> equip(id)
                    else -> preview(id)
                }
            }
            "tab" -> TABS[c.tab]?.let(::selectTab)
        }
    }

    override fun onCleared() = host.cancelAudition()

    companion object {
        const val BUILD_PREVIEW = "build"
        const val DEMO_POINTS = 1000
        private const val POLL_MS = 200L
        private val DEMO_SELECTION = mapOf(Category.ENGINE to "v8", Category.INTAKE to "t3", Category.EXHAUST to "track", Category.TRANSMISSION to "mt")
        private val TABS = mapOf(
            "engine" to GarageTab.ENGINE, "intake" to GarageTab.INTAKE, "exhaust" to GarageTab.EXHAUST,
            "trans" to GarageTab.TRANSMISSION, "tune" to GarageTab.TUNE,
        )
    }
}
