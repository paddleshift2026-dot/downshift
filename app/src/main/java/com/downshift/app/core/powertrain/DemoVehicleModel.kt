package com.downshift.app.core.powertrain

import com.downshift.app.core.model.GearState

/**
 * Turns pedals into road speed for the emulator demo, where the Virtual Console has no physics.
 * Tuned to feel like a hot hatch: about 7.5 s to 100 km/h, rolling and air drag when coasting, hard braking.
 */
class DemoVehicleModel(
    initialMps: Double = 0.0,
    val maxAccel: Double = 4.5,
    val topSpeedMps: Double = 55.0,
    val maxBrake: Double = 8.0,
) {
    var speedMps: Double = initialMps
        private set

    fun step(throttle: Double, brake: Double, gear: GearState, dtSec: Double): Double {
        val v = speedMps
        val drives = gear == GearState.DRIVE || gear == GearState.REVERSE
        val push = if (drives) throttle.coerceIn(0.0, 1.0) * maxAccel * (1 - (v / topSpeedMps).let { it * it }) else 0.0
        val a = push - ROLLING - AIR * v * v - brake.coerceIn(0.0, 1.0) * maxBrake
        val cap = if (gear == GearState.REVERSE) REVERSE_MAX_MPS else topSpeedMps
        speedMps = (v + a * dtSec).coerceIn(0.0, cap)
        return speedMps
    }

    private companion object {
        const val ROLLING = 0.15
        const val AIR = 0.00045
        const val REVERSE_MAX_MPS = 10.0 / 3.6
    }
}
