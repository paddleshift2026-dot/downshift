package com.downshift.app.core.input

import kotlin.math.exp

/**
 * Car speed arrives 10 to 20 times a second in whole-km/h steps; fed straight to the powertrain, a pulling gear's rpm
 * would climb in audible stairs. A short first-order lag turns the stairs into a ramp; the first sample passes through.
 */
class SpeedSmoother(private val tauSec: Double = 0.1) {
    private var value = Double.NaN

    fun step(speedMps: Double, dtSec: Double): Double {
        value = if (value.isNaN()) speedMps else speedMps + (value - speedMps) * exp(-dtSec / tauSec)
        return value
    }
}
