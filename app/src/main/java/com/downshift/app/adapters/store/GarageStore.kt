package com.downshift.app.adapters.store

import android.content.Context
import com.downshift.app.core.garage.GarageCodec
import com.downshift.app.core.garage.GarageState

/**
 * The garage saved as one JSON value in SharedPreferences ("garage", key "state"). Reading never fails (GarageCodec
 * starts fresh from anything broken), and every save is handed to SharedPreferences at once.
 */
class GarageStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): GarageState = GarageCodec.decode(prefs.getString(KEY, null))

    fun save(s: GarageState) = prefs.edit().putString(KEY, GarageCodec.encode(s)).apply()

    private companion object {
        const val FILE = "garage"
        const val KEY = "state"
    }
}
