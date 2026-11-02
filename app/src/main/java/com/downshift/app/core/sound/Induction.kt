package com.downshift.app.core.sound

import com.downshift.app.core.build.IntakePart
import kotlin.math.exp

/** boost is in bar; whistle is the turbo spool sound, whine the supercharger gear noise. */
data class InductionState(val boost: Double, val whistleHz: Double, val whistleGain: Double, val whineHz: Double, val whineGain: Double) {
    companion object {
        val ZERO = InductionState(0.0, 0.0, 0.0, 0.0, 0.0)
    }
}

data class InductionStep(val state: InductionState, val blowOff: Boolean) {
    companion object {
        val NONE = InductionStep(InductionState.ZERO, false)
    }
}

/**
 * Turbo boost that builds with load and rpm and lags behind the pedal (bigger turbos spool slower),
 * a blow-off valve on the bigger turbos when lifting under boost, and a supercharger whine locked to rpm.
 */
class InductionModel(var part: IntakePart) {
    private var boost = 0.0
    private var armed = false

    fun step(rpm: Double, redline: Double, load: Double, dtSec: Double): InductionStep {
        val max = maxBoost(part)
        return when (part) {
            IntakePart.NA -> {
                boost = 0.0
                armed = false
                InductionStep.NONE
            }
            IntakePart.SUPERCHARGER -> {
                boost = max * load * (rpm / redline).coerceIn(0.0, 1.0)
                InductionStep(InductionState(boost, 0.0, 0.0, WHINE_RATIO * rpm, WHINE_BASE + WHINE_LOAD * load), false)
            }
            else -> {
                val x = ((rpm - SPOOL_START_RPM) / SPOOL_SPAN_RPM).coerceIn(0.0, 1.0)
                val target = max * load * x * x * (3 - 2 * x)
                val tau = if (target > boost) spoolTau(part) else BLEED_TAU
                boost = target + (boost - target) * exp(-dtSec / tau)
                var blowOff = false
                if (load >= ARM_LOAD) armed = true
                if (armed && load < FIRE_LOAD) {
                    armed = false
                    blowOff = part != IntakePart.TURBO1 && boost >= BLOW_OFF_MIN_FRACTION * max
                }
                val f = boost / max
                InductionStep(InductionState(boost, WHISTLE_BASE_HZ + WHISTLE_SPAN_HZ * f, whistleGain(part) * f, 0.0, 0.0), blowOff)
            }
        }
    }

    companion object {
        private const val SPOOL_START_RPM = 1500.0
        private const val SPOOL_SPAN_RPM = 2000.0
        private const val BLEED_TAU = 0.25
        private const val ARM_LOAD = 0.3
        private const val FIRE_LOAD = 0.1
        private const val BLOW_OFF_MIN_FRACTION = 0.4
        private const val WHISTLE_BASE_HZ = 1800.0
        private const val WHISTLE_SPAN_HZ = 5200.0
        private const val WHINE_RATIO = 0.18
        private const val WHINE_BASE = 0.15
        private const val WHINE_LOAD = 0.35

        fun maxBoost(part: IntakePart): Double = when (part) {
            IntakePart.NA -> 0.0
            IntakePart.TURBO1 -> 0.5
            IntakePart.TURBO2 -> 0.75
            IntakePart.TURBO3 -> 1.0
            IntakePart.SUPERCHARGER -> 0.6
        }

        private fun spoolTau(part: IntakePart) = when (part) {
            IntakePart.TURBO1 -> 0.5
            IntakePart.TURBO2 -> 0.8
            else -> 1.2
        }

        private fun whistleGain(part: IntakePart) = when (part) {
            IntakePart.TURBO1 -> 0.2
            IntakePart.TURBO2 -> 0.35
            else -> 0.5
        }
    }
}
