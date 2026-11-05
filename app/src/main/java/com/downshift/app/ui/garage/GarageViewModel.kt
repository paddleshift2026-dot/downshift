package com.downshift.app.ui.garage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.downshift.app.DownshiftApp
import com.downshift.app.DriveLive
import com.downshift.app.adapters.demo.DemoBus
import com.downshift.app.adapters.demo.DemoCommand
import com.downshift.app.adapters.demo.PedalStatus
import com.downshift.app.core.garage.Catalog
import com.downshift.app.core.garage.Category
import com.downshift.app.core.garage.Garage
import com.downshift.app.core.garage.GarageState
import com.downshift.app.core.garage.Part
import com.downshift.app.core.garage.PartKind
import com.downshift.app.core.garage.Tune
import com.downshift.app.core.voice.VoiceSlot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
    /** The voice slots that have a take (VoiceHost). */
    val recorded: Set<VoiceSlot> = emptySet(),
) {
    /** The card picked in this tab, or null in the tune tab. */
    val selectedPart: Part? get() = tab.category?.let { c -> selected[c]?.let(Catalog::part) }

    /** The voice card is the one picked in this tab. */
    val voicePicked: Boolean get() = tab.category?.let { selected[it] == GarageViewModel.VOICE_ID } ?: false
}

fun fittedOf(s: GarageState): Map<Category, String> = Category.entries.associateWith { Garage.fitted(s, it) }

/**
 * The garage's state and moves: pick parts, preview them (even locked ones), unlock with points, equip, tune, and the
 * demo buttons. Every change goes through the app's one GarageRepository (saved at once, worked out from its latest
 * state, so a trip's points arriving there are never overwritten), and an equip or tune goes straight to the shared
 * engine. Changes need the car parked; the demo buttons (demo mode only, through DemoHost) always work. adb (DemoBus)
 * reaches the same moves: add and reset in DemoHost, the taps here.
 */
class GarageViewModel(app: Application) : AndroidViewModel(app) {
    private val host = (app as DownshiftApp).host
    private val repo = (app as DownshiftApp).garage
    private val drive = (app as DownshiftApp).drive
    private val demo = (app as DownshiftApp).demo
    private val voice = (app as DownshiftApp).voice

    private val _ui = MutableStateFlow(GarageUi())
    val ui: StateFlow<GarageUi> = _ui.asStateFlow()
    private val _rpm = MutableStateFlow(0)
    /** The engine's rpm to the nearest 10, 0 when it is off; read it while drawing. */
    val rpm: StateFlow<Int> = _rpm.asStateFlow()

    init {
        val s = repo.state.value
        _ui.value = GarageUi(state = s, selected = fittedOf(s))
        host.setBuild(Garage.build(s))
        viewModelScope.launch { repo.state.collect { saved -> _ui.update { it.copy(state = saved) } } }
        viewModelScope.launch { voice.recorded.collect { r -> _ui.update { it.copy(recorded = r) } } }
        // Switching wallets (demo mode) brings another garage: pick its fitted parts.
        viewModelScope.launch { repo.demo.drop(1).collect { _ui.update { it.copy(selected = fittedOf(repo.state.value), playing = null) } } }
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

    fun selectVoice(category: Category) = _ui.update { it.copy(selected = it.selected + (category to VOICE_ID)) }

    fun setVoiceOn(category: Category, on: Boolean) {
        if (!host.parked) return
        voice.setOn(category, on)
    }

    /** The tab's preview with its saved takes, heard even while the card is off; again stops it. */
    fun previewVoice(category: Category) {
        val id = voiceId(category)
        val u = _ui.value
        if (u.playing == id) {
            host.cancelAudition()
            return
        }
        if (!host.parked || VoiceSlot.of(category).none { it in u.recorded }) return
        _ui.update { it.copy(playing = id) }
        voice.audition(category, null) { _ui.update { if (it.playing == id) it.copy(playing = null) else it } }
    }

    fun deleteVoices() {
        if (host.parked) voice.deleteAll()
    }

    fun unlock(id: String) {
        if (!host.parked) return
        commit { Garage.unlock(it, id) ?: it }
    }

    fun equip(id: String) {
        if (!host.parked) return
        if (Garage.equip(repo.state.value, id) == null) return
        _ui.update { it.copy(equipSeq = it.equipSeq + 1, equipCategory = Catalog.part(id)?.category) }
        commit(toEngine = true) { Garage.equip(it, id) ?: it }
    }

    fun setTune(t: Tune) {
        if (!host.parked) return
        commit(toEngine = true) { Garage.tune(it, t) }
    }

    /** Demo mode, its dashed buttons, and the drive's lapse (in DriveLive), for the tune tab and the header. */
    val demoOn: StateFlow<Boolean> = demo.on
    val demoButtons: StateFlow<Boolean> = demo.buttons
    val live: StateFlow<DriveLive> = drive.live
    val pedal: StateFlow<PedalStatus> = demo.pedal

    /** False, with nothing changed, while a trip is being recorded. */
    fun setDemo(on: Boolean): Boolean = demo.setOn(on)

    fun setDemoButtons(show: Boolean) = demo.setButtons(show)

    fun setLapse(x: Int) = demo.setLapse(x)

    /** The start screen holds the ignition now; the garage only opens it. */
    fun openStart() = drive.openStart()

    fun reviewSafety() = drive.reviewSafety()

    /** Demo mode's +1,000 P, into the demo wallet (DemoHost). */
    fun demoAddPoints() = demo.addPoints()

    /** The design's demo state in the demo wallet, with the cards the PT walks through picked. */
    fun demoReset() {
        if (!demo.on.value) return
        demo.reset()
        pickDemoCards()
    }

    private fun pickDemoCards() = _ui.update { it.copy(selected = DEMO_SELECTION, playing = null) }

    private fun commit(toEngine: Boolean = false, change: (GarageState) -> GarageState) {
        val s = repo.update(change)
        _ui.update { it.copy(state = s) }
        if (toEngine) host.setBuild(Garage.build(s))
    }

    private fun poll() {
        val t = host.telemetry
        _rpm.value = if (t.running) (t.rpm / 10).roundToInt() * 10 else 0
        _ui.update { it.copy(parked = host.parked, ready = host.ready, engineOn = host.engineOn, packSource = host.packSource) }
    }

    /** adb garage moves that mimic taps on this screen; add and reset change the wallet in DemoHost, with no screen up. */
    private fun handle(c: DemoCommand) {
        if (c !is DemoCommand.GarageAction) return
        when (c.action) {
            "reset" -> if (demo.on.value) pickDemoCards()
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
        const val VOICE_ID = "voice"
        fun voiceId(category: Category) = "voice:${category.name}"
        private const val POLL_MS = 200L
        private val DEMO_SELECTION = mapOf(Category.ENGINE to "v8", Category.INTAKE to "t3", Category.EXHAUST to "track", Category.TRANSMISSION to "mt")
        private val TABS = mapOf(
            "engine" to GarageTab.ENGINE, "intake" to GarageTab.INTAKE, "exhaust" to GarageTab.EXHAUST,
            "trans" to GarageTab.TRANSMISSION, "tune" to GarageTab.TUNE,
        )
    }
}
