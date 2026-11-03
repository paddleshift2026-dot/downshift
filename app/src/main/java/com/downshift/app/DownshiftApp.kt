package com.downshift.app

import android.app.Application
import com.downshift.app.adapters.store.GarageRepository
import com.downshift.app.core.garage.Garage

class DownshiftApp : Application() {
    val garage: GarageRepository by lazy { GarageRepository(this) }

    /** The one engine every screen shares, made on first use and fitted with the saved garage build. */
    val host: EngineHost by lazy { EngineHost(this).also { it.setBuild(Garage.build(garage.state.value)) } }
}
