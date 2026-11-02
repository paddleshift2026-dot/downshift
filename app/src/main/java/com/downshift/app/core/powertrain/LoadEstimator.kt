package com.downshift.app.core.powertrain

import kotlin.math.exp

/**
 * Guesses engine load from road speed alone, for real cars where apps cannot read the pedal.
 * Call it on every speed sample with the time since the previous one: the smoothed acceleration maps to
 * a light cruise load at 0 m/s², full load at fullLoadAccel, and overrun (0) when clearly slowing.
 */
class LoadEstimator(private val fullLoadAccel: Double = 3.0, private val tauSec: Double = 0.25) {
    private var lastSpeed = Double.NaN
    private var accel = 0.0
    private var load = 0.0

    fun update(speedMps: Double, dtSec: Double): Double {
        if (dtSec <= 0.0) return load
        val raw = if (lastSpeed.isNaN()) 0.0 else (speedMps - lastSpeed) / dtSec
        lastSpeed = speedMps
        accel += (raw - accel) * (1 - exp(-dtSec / tauSec))
        load = when {
            speedMps < MOVING_MPS -> 0.0
            accel < BRAKING_ACCEL -> 0.0
            else -> (CRUISE_LOAD + accel / fullLoadAccel * (1 - CRUISE_LOAD)).coerceIn(0.0, 1.0)
        }
        return load
    }

    companion object {
        const val CRUISE_LOAD = 0.2
        private const val MOVING_MPS = 1.0
        private const val BRAKING_ACCEL = -0.3
    }
}
