package com.downshift.app.adapters.store

import android.content.Context
import com.downshift.app.core.garage.Garage
import com.downshift.app.core.garage.GarageCodec
import com.downshift.app.core.garage.GarageState
import com.downshift.app.core.trip.TripCheckpoint
import com.downshift.app.core.trip.TripCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's garage, in SharedPreferences "garage": two wallets as JSON (the real one under M2's key, so saved garages
 * carry over, and the demo one, PRD feature G: demo points are kept apart from the real balance), which of them is in
 * use, a running trip's checkpoint and the safety consent. Every change goes through update(), computed from the latest
 * state of the wallet in use and saved at once, so the garage screen and a trip's points cannot overwrite each other.
 * Finishing a trip adds its points to the wallet it was driven in and clears its checkpoint in one write, so a trip is
 * credited once even if the app dies right after. Trip writes come from the drive thread and are committed synchronously.
 */
class GarageRepository(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _demo = MutableStateFlow(prefs.getBoolean(DEMO, false))
    /** True while demo mode is on: the demo wallet is the one in use. */
    val demo: StateFlow<Boolean> = _demo.asStateFlow()
    private val _state = MutableStateFlow(load(_demo.value))
    /** The wallet in use. */
    val state: StateFlow<GarageState> = _state.asStateFlow()

    fun update(f: (GarageState) -> GarageState): GarageState = synchronized(this) {
        val now = _state.value
        val next = f(now)
        if (next != now) {
            prefs.edit().putString(key(_demo.value), GarageCodec.encode(next)).apply()
            _state.value = next
        }
        next
    }

    /** [f] applied to both wallets (spec 7: deleting the takes turns every voice card off in both). */
    fun updateAll(f: (GarageState) -> GarageState): GarageState = synchronized(this) {
        val other = !_demo.value
        prefs.edit().putString(key(other), GarageCodec.encode(f(load(other)))).apply()
        update(f)
    }

    /** Switches to the demo wallet or back to the real one; returns the wallet now in use. */
    fun setDemo(on: Boolean): GarageState = synchronized(this) {
        if (on != _demo.value) {
            prefs.edit().putBoolean(DEMO, on).commit()
            _demo.value = on
            _state.value = load(on)
        }
        _state.value
    }

    /** Whether the dashed demo buttons are shown while demo mode is on. */
    var demoButtons: Boolean
        get() = prefs.getBoolean(DEMO_BUTTONS, true)
        set(v) = prefs.edit().putBoolean(DEMO_BUTTONS, v).apply()

    var consentVersion: Int
        get() = prefs.getInt(CONSENT, 0)
        set(v) = prefs.edit().putInt(CONSENT, v).apply()

    fun loadTrip(): TripCheckpoint? = TripCodec.decode(prefs.getString(TRIP, null))

    fun saveTrip(c: TripCheckpoint) {
        prefs.edit().putString(TRIP, TripCodec.encode(c)).commit()
    }

    /** Adds a finished trip's points to the wallet it was driven in and forgets its checkpoint in the same write. */
    fun finishTrip(points: Int, demo: Boolean): GarageState = synchronized(this) {
        val inUse = demo == _demo.value
        val next = Garage.addPoints(if (inUse) _state.value else load(demo), points)
        prefs.edit().putString(key(demo), GarageCodec.encode(next)).remove(TRIP).commit()
        if (inUse) _state.value = next
        next
    }

    private fun key(demo: Boolean) = if (demo) DEMO_STATE else STATE

    /** A wallet as saved; a demo wallet that was never saved starts as the design's demo state. */
    private fun load(demo: Boolean): GarageState =
        if (demo) GarageCodec.decode(prefs.getString(DEMO_STATE, null), GarageState.DEMO) else GarageCodec.decode(prefs.getString(STATE, null))

    private companion object {
        const val FILE = "garage"
        const val STATE = "state"
        const val DEMO_STATE = "demo_state"
        const val DEMO = "demo"
        const val DEMO_BUTTONS = "demo_buttons"
        const val TRIP = "trip"
        const val CONSENT = "consent"
    }
}
