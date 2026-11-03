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
 * The app's one garage, in SharedPreferences "garage": the state as JSON (M2's key, so saved garages carry over), a
 * running trip's checkpoint and the safety consent. Every change goes through update(), computed from the latest
 * state and saved at once, so the garage screen and a trip's points cannot overwrite each other. Finishing a trip adds
 * its points and clears its checkpoint in one write, so a trip is credited once even if the app dies right after.
 * Trip writes come from the drive thread and are committed synchronously.
 */
class GarageRepository(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(GarageCodec.decode(prefs.getString(STATE, null)))
    val state: StateFlow<GarageState> = _state.asStateFlow()

    fun update(f: (GarageState) -> GarageState): GarageState = synchronized(this) {
        val now = _state.value
        val next = f(now)
        if (next != now) {
            prefs.edit().putString(STATE, GarageCodec.encode(next)).apply()
            _state.value = next
        }
        next
    }

    var consentVersion: Int
        get() = prefs.getInt(CONSENT, 0)
        set(v) = prefs.edit().putInt(CONSENT, v).apply()

    fun loadTrip(): TripCheckpoint? = TripCodec.decode(prefs.getString(TRIP, null))

    fun saveTrip(c: TripCheckpoint) {
        prefs.edit().putString(TRIP, TripCodec.encode(c)).commit()
    }

    /** Adds a finished trip's points and forgets its checkpoint in the same write. */
    fun finishTrip(points: Int): GarageState = synchronized(this) {
        val next = Garage.addPoints(_state.value, points)
        prefs.edit().putString(STATE, GarageCodec.encode(next)).remove(TRIP).commit()
        _state.value = next
        next
    }

    private companion object {
        const val FILE = "garage"
        const val STATE = "state"
        const val TRIP = "trip"
        const val CONSENT = "consent"
    }
}
