package com.downshift.app

import com.downshift.app.core.trip.PlaceAhead
import com.downshift.app.core.trip.TurnAhead

/** What the navigation, the car and adb hand the drive thread; DriveHost applies them at its next tick. */
sealed class DriveEvent {
    /** The road's speed limit in km/h, 0 when unknown. */
    data class Limit(val kph: Int) : DriveEvent()
    data class Turn(val turn: TurnAhead) : DriveEvent()
    data class Place(val place: PlaceAhead) : DriveEvent()
    /** A cumulative odometer reading in km. */
    data class Odometer(val km: Double) : DriveEvent()
    /** Inside or outside a quiet zone (residential areas; injected over adb until there is geofence data). */
    data class Quiet(val on: Boolean) : DriveEvent()
    /** Demo only: count the distance this many times over (1 = off). */
    data class Lapse(val factor: Int) : DriveEvent()
}
