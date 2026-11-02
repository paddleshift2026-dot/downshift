package com.downshift.app.core.powertrain

import com.downshift.app.core.build.TransmissionPart
import kotlin.math.PI
import kotlin.math.pow

const val WHEEL_RADIUS_M = 0.33

/** Gear ratios from first gear up, the final drive, and how long one shift takes. */
data class GearSet(val ratios: List<Double>, val finalDrive: Double, val shiftMs: Long)

object Transmissions {
    private val AT = GearSet(listOf(4.70, 3.13, 2.10, 1.67, 1.29, 1.00, 0.84, 0.67), 3.20, 280)
    private val DCT = GearSet(listOf(3.64, 2.18, 1.48, 1.12, 0.92, 0.77, 0.64), 3.70, 120)
    private val MT = GearSet(listOf(3.54, 2.13, 1.48, 1.16, 0.97, 0.82), 3.90, 420)

    fun of(part: TransmissionPart): GearSet = when (part) {
        TransmissionPart.AT -> AT
        TransmissionPart.DCT -> DCT
        TransmissionPart.MT_BLIP -> MT
    }
}

/** Engine rpm with the clutch closed: wheel revolutions per minute × gear ratio × final drive. */
fun coupledRpm(speedMps: Double, ratio: Double, finalDrive: Double, wheelRadiusM: Double = WHEEL_RADIUS_M): Double =
    speedMps / (2 * PI * wheelRadiusM) * 60.0 * ratio * finalDrive

/** Throttle-dependent shift points, the same shape for every gearbox: gentle driving shifts early, full throttle near redline. */
object ShiftSchedule {
    fun upshiftRpm(throttle: Double, redline: Double): Double =
        lerp(2200.0, 0.95 * redline, throttle.coerceIn(0.0, 1.0).pow(1.3))

    fun downshiftRpm(throttle: Double, redline: Double): Double =
        lerp(1100.0, 0.5 * redline, throttle.coerceIn(0.0, 1.0))

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
}
