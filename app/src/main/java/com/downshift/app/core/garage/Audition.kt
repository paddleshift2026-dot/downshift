package com.downshift.app.core.garage

import com.downshift.app.core.model.DriveInputs
import com.downshift.app.core.model.GearState
import com.downshift.app.core.powertrain.DemoVehicleModel

/**
 * A short scripted drive that shows off one kind of part, played instead of the real pedals while previewing.
 * Engine and intake scripts rev in park; exhaust and transmission scripts drive a virtual car of their own (harder
 * accelerating and braking than the demo car, so a few seconds reach second and third gear) and park once it stops.
 * cold puts a two-second engine start in front, for when the engine was off.
 */
class Audition(val category: Category, cold: Boolean) {
    private class Phase(val sec: Double, val throttle: Double, val brake: Double = 0.0)

    private val drives = category == Category.EXHAUST || category == Category.TRANSMISSION
    private val phases = when (category) {
        Category.ENGINE -> listOf(Phase(0.8, 0.0), Phase(1.2, 0.75), Phase(1.6, 0.0))
        Category.INTAKE -> listOf(Phase(0.6, 0.0), Phase(2.2, 0.6), Phase(1.4, 0.0)) // one blow-off on the lift
        Category.EXHAUST -> listOf(Phase(2.6, 0.8), Phase(2.0, 0.0), Phase(2.5, 0.0, brake = 1.0)) // overrun in gear
        Category.TRANSMISSION -> listOf(Phase(3.6, 0.8), Phase(1.2, 0.0), Phase(3.0, 0.0, brake = 1.0)) // two up, down with blips
    }
    private val lead = if (cold) COLD_START_SEC else 0.0
    val durationSec: Double = lead + phases.sumOf { it.sec }

    private val car = DemoVehicleModel(maxAccel = MAX_ACCEL, maxBrake = MAX_BRAKE)
    private var carT = lead
    private var parked = false

    /** The inputs at [tSec] from the start; the virtual car rolls forward to it, so t must only grow. */
    fun inputs(tSec: Double): DriveInputs {
        if (tSec < lead) return PARKED
        if (!drives) return DriveInputs(phaseAt(tSec).throttle, 0.0, 0.0, GearState.PARK)
        while (carT < tSec && !parked) {
            val h = minOf(MAX_STEP_SEC, tSec - carT)
            val p = phaseAt(carT)
            car.step(p.throttle, p.brake, GearState.DRIVE, h)
            carT += h
            if (p.brake > 0.0 && car.speedMps <= STOPPED_MPS) parked = true
        }
        if (parked) return PARKED
        val p = phaseAt(tSec)
        return DriveInputs(p.throttle, p.brake, car.speedMps, GearState.DRIVE)
    }

    private fun phaseAt(tSec: Double): Phase {
        var left = tSec - lead
        for (p in phases) {
            if (left < p.sec) return p
            left -= p.sec
        }
        return phases.last()
    }

    private companion object {
        const val COLD_START_SEC = 2.0
        const val MAX_ACCEL = 9.0
        const val MAX_BRAKE = 10.0
        const val MAX_STEP_SEC = 0.01
        const val STOPPED_MPS = 0.05
        val PARKED = DriveInputs(0.0, 0.0, 0.0, GearState.PARK)
    }
}
