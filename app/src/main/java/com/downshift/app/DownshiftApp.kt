package com.downshift.app

import android.app.Application
import com.downshift.app.adapters.store.GarageRepository
import com.downshift.app.core.garage.Garage

class DownshiftApp : Application() {
    val garage: GarageRepository by lazy { GarageRepository(this) }

    /** The one engine every screen shares, made on first use and fitted with the saved garage build. */
    val host: EngineHost by lazy { EngineHost(this).also { it.setBuild(Garage.build(garage.state.value)) } }

    /** Trips, points and the situation volume; made with the process so the gear is watched with no screen up. */
    val drive: DriveHost by lazy { DriveHost(this, host, garage) }

    /** Demo mode and its wallet; made with the process so adb's demo and garage commands work with no screen up. */
    val demo: DemoHost by lazy { DemoHost(this, garage, host, drive) }

    override fun onCreate() {
        super.onCreate()
        drive
        demo
    }
}
