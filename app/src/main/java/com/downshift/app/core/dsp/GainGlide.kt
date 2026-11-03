package com.downshift.app.core.dsp

import kotlin.math.exp

/**
 * A gain that moves toward its target once per block: down quickly (attackSec), back up slowly (releaseSec), so a
 * duck under guidance is prompt and the return is not a jump.
 */
class GainGlide(initial: Double = 1.0, private val attackSec: Double = 0.15, private val releaseSec: Double = 0.6) {
    var value = initial
        private set

    fun next(target: Double, dtSec: Double): Double {
        val tau = if (target < value) attackSec else releaseSec
        value += (target - value) * (1 - exp(-dtSec / tau))
        return value
    }
}
