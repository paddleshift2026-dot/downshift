package com.downshift.app.core.engine

import com.downshift.app.core.build.EngineId

/** How an engine revs: rpm limits, and the time constants of a free rev rising (riseTau) and falling (fallTau). */
data class EngineSpec(
    val id: EngineId,
    val cylinders: Int,
    val idleRpm: Double,
    val redlineRpm: Double,
    val limiterRpm: Double,
    val riseTau: Double,
    val fallTau: Double,
    val firingOrder: String,
) {
    /** Moves redline and limiter together by the garage slider, clamped to ±500 rpm. */
    fun withRedlineOffset(offset: Int): EngineSpec {
        val o = offset.coerceIn(-MAX_REDLINE_OFFSET, MAX_REDLINE_OFFSET).toDouble()
        return copy(redlineRpm = redlineRpm + o, limiterRpm = limiterRpm + o)
    }

    companion object {
        const val MAX_REDLINE_OFFSET = 500
    }
}

object Engines {
    private val specs = mapOf(
        EngineId.I4 to EngineSpec(EngineId.I4, 4, 880.0, 6800.0, 7000.0, 0.16, 0.35, "1-3-4-2"),
        EngineId.V8 to EngineSpec(EngineId.V8, 8, 720.0, 6500.0, 6700.0, 0.22, 0.45, "1-8-4-3-6-5-7-2"),
        EngineId.V12 to EngineSpec(EngineId.V12, 12, 760.0, 8000.0, 8250.0, 0.18, 0.40, "1-7-5-11-3-9-6-12-2-8-4-10"),
    )

    fun spec(id: EngineId): EngineSpec = specs.getValue(id)
}
