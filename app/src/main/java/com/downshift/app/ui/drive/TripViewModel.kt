package com.downshift.app.ui.drive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.downshift.app.DownshiftApp
import com.downshift.app.DriveLive
import com.downshift.app.core.garage.Garage
import com.downshift.app.core.garage.GarageState
import com.downshift.app.core.garage.Turntable
import com.downshift.app.core.trip.SummaryView
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The fitted build as the drive screen shows it: the plate and which render to draw. */
data class BuildLook(val plate: String, val variant: String)

/**
 * The drive and summary screens' window on DriveHost: the live ring and situation (published only when they change),
 * the last trip's summary, and the moves those screens have (mute, the demo's arrive, back to the garage).
 */
class TripViewModel(app: Application) : AndroidViewModel(app) {
    private val drive = (app as DownshiftApp).drive
    private val garage = (app as DownshiftApp).garage

    val live: StateFlow<DriveLive> = drive.live
    val summary: StateFlow<SummaryView?> = drive.summary
    val build: StateFlow<BuildLook> = garage.state.map(::look).stateIn(viewModelScope, SharingStarted.Eagerly, look(garage.state.value))

    fun setMuted(on: Boolean) = drive.setMuted(on)

    fun arrive() = drive.demoArrive()

    fun toGarage() = drive.back()

    private fun look(s: GarageState) = BuildLook(Garage.plate(Garage.build(s)), Turntable.variant(s.engine, s.intake))
}
