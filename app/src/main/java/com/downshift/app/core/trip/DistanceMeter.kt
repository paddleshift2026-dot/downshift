package com.downshift.app.core.trip

import kotlin.math.roundToInt

/**
 * A trip's distance, split by the road's speed limit. The total is the car's odometer once it has moved a tenth of a
 * kilometre (on a real car, Pleos registerDistanceDriven), otherwise the speed integral. The split into kept, over and
 * unknown-limit distance always comes from the integral, scaled to that total, so a coarse odometer still splits
 * correctly. Speeds meet the limit in whole km/h, as a speedometer shows them.
 */
class DistanceMeter {
    var integralKm = 0.0
        private set
    var odometerKm = 0.0
        private set
    private var knownIntegral = 0.0
    private var keptIntegral = 0.0
    private var unknownIntegral = 0.0
    private var lastOdometer: Double? = null

    /** limitKph 0: the navigation gave no limit here. */
    fun step(speedMps: Double, limitKph: Int, dtSec: Double) {
        val km = speedMps.coerceAtLeast(0.0) * dtSec / 1000.0
        integralKm += km
        if (limitKph > 0) {
            knownIntegral += km
            if ((speedMps * 3.6).roundToInt() <= limitKph) keptIntegral += km
        } else {
            unknownIntegral += km
        }
    }

    /** A cumulative odometer reading in km. A step backwards or a jump of more than MAX_JUMP_KM counts on from there. */
    fun odometer(km: Double) {
        val last = lastOdometer
        lastOdometer = km
        if (last == null) return
        val d = km - last
        if (d > 0 && d <= MAX_JUMP_KM) odometerKm += d
    }

    val totalKm: Double get() = if (odometerKm >= MIN_ODOMETER_KM) odometerKm else integralKm
    val knownKm: Double get() = share(knownIntegral)
    val keptKm: Double get() = share(keptIntegral)

    /** With no speed integral at all (no speed signal), everything the odometer saw has an unknown limit. */
    val unknownKm: Double get() = if (integralKm > 0) share(unknownIntegral) else totalKm

    private fun share(part: Double) = if (integralKm > 0) part * totalKm / integralKm else 0.0

    private companion object {
        const val MIN_ODOMETER_KM = 0.1
        const val MAX_JUMP_KM = 5.0
    }
}
