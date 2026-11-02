package com.downshift.app.core.sound

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The two neighbouring bands to sound at one rpm, with equal-power gains (lowGain² + highGain² = 1). */
data class BandWeights(val low: Int, val high: Int, val lowGain: Double, val highGain: Double)

object BandLayout {
    /** Where the recorded loops sit between idle (0) and redline (1). */
    val DEFAULT_FRACTIONS = listOf(0.0, 0.12, 0.25, 0.38, 0.5, 0.63, 0.78, 0.92)

    fun defaultRpms(idle: Double, redline: Double): List<Double> = DEFAULT_FRACTIONS.map { idle + it * (redline - idle) }

    /** Crossfades between the bands either side of rpm; outside the ladder the end band plays alone. */
    fun weights(rpms: List<Double>, rpm: Double): BandWeights {
        val last = rpms.lastIndex
        if (last <= 0 || rpm <= rpms[0]) return BandWeights(0, 0, 1.0, 0.0)
        if (rpm >= rpms[last]) return BandWeights(last, last, 1.0, 0.0)
        var i = 0
        while (rpm >= rpms[i + 1]) i++
        val x = (rpm - rpms[i]) / (rpms[i + 1] - rpms[i])
        return BandWeights(i, i + 1, cos(x * PI / 2), sin(x * PI / 2))
    }
}
