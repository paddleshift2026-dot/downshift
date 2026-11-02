package com.downshift.app.core.input

enum class ThrottleSource { CONSOLE, MANUAL, ESTIMATE }

data class ArbitratedInputs(val throttle: Double, val brake: Double, val source: ThrottleSource)

/**
 * Picks the pedal to drive the engine with: a fresh Virtual Console reading first, then a manual value
 * (screen slider or adb), then the speed-based estimate. Writers and the reader may be on different threads.
 */
class InputArbiter(private val staleMs: Long = 500) {
    private class ConsoleSample(val throttle: Double, val brake: Double, val atMs: Long)

    @Volatile private var consoleSample: ConsoleSample? = null
    @Volatile private var manualThrottle: Double? = null
    @Volatile private var carBrake = false

    fun console(throttle: Double, brake: Double, atMs: Long) {
        consoleSample = ConsoleSample(throttle.coerceIn(0.0, 1.0), brake.coerceIn(0.0, 1.0), atMs)
    }

    fun manual(throttle: Double?) {
        manualThrottle = throttle?.coerceIn(0.0, 1.0)
    }

    fun vehicleBrake(pressed: Boolean) {
        carBrake = pressed
    }

    fun pick(nowMs: Long, estimated: Double): ArbitratedInputs {
        val c = consoleSample
        if (c != null && nowMs - c.atMs <= staleMs) return ArbitratedInputs(c.throttle, c.brake, ThrottleSource.CONSOLE)
        val brake = if (carBrake) 1.0 else 0.0
        val m = manualThrottle
        return if (m != null) ArbitratedInputs(m, brake, ThrottleSource.MANUAL) else ArbitratedInputs(estimated, brake, ThrottleSource.ESTIMATE)
    }
}
