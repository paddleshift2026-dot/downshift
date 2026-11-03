package com.downshift.app.core.synth

import com.downshift.app.core.build.EngineId
import com.downshift.app.core.engine.EngineSpec

/** One firing in a four-stroke cycle: the crank angle (0..720°), which cylinder, and which exhaust bank it blows into. */
data class FiringEvent(val angleDeg: Double, val cylinder: Int, val bank: Int)

object FiringPlans {
    /** Evenly spaced firings in the spec's firing order. A V engine's banks then see uneven gaps (the cross-plane burble). */
    fun of(spec: EngineSpec): List<FiringEvent> {
        val order = spec.firingOrder.split('-').map { it.trim().toInt() }
        require(order.size == spec.cylinders) { "firing order ${spec.firingOrder} does not name ${spec.cylinders} cylinders" }
        val step = 720.0 / spec.cylinders
        return order.mapIndexed { i, cylinder -> FiringEvent(i * step, cylinder, bankOf(spec.id, cylinder)) }
    }

    fun banks(id: EngineId): Int = if (id == EngineId.I4) 1 else 2

    private fun bankOf(id: EngineId, cylinder: Int) = when (id) {
        EngineId.I4 -> 0
        EngineId.V8 -> if (cylinder % 2 == 1) 0 else 1 // GM numbering: odd cylinders on the left bank
        EngineId.V12 -> if (cylinder <= 6) 0 else 1
    }
}
