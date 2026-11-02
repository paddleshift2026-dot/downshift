package com.downshift.app.core.model

/** What the gear selector says. Inside DRIVE the virtual powertrain picks its own gear. */
enum class GearState { UNKNOWN, PARK, REVERSE, NEUTRAL, DRIVE }

/** One control tick: pedals in 0..1, road speed, and the selector. */
data class DriveInputs(val throttle: Double, val brake: Double, val speedMps: Double, val gear: GearState)

/** The car's own signals as the adapters last saw them. */
data class VehicleSignals(
    val speedMps: Double = 0.0,
    val gear: GearState = GearState.UNKNOWN,
    val brakePressed: Boolean = false,
    val fuel: FuelKind = FuelKind.UNKNOWN,
)

enum class FuelKind {
    EV, HYBRID, ICE, UNKNOWN;

    companion object {
        private const val ELECTRIC = 10 // android.car FuelType.ELECTRIC
        private val COMBUSTION = (1..9) + (11..12)

        /** Classifies the AAOS INFO_FUEL_TYPE array: electric only is an EV, electric plus a fuel is a hybrid. */
        fun from(fuelTypes: IntArray?): FuelKind {
            if (fuelTypes == null || fuelTypes.isEmpty()) return UNKNOWN
            val electric = ELECTRIC in fuelTypes
            val combustion = fuelTypes.any { it in COMBUSTION }
            return when {
                electric && combustion -> HYBRID
                electric -> EV
                combustion -> ICE
                else -> UNKNOWN
            }
        }
    }
}
