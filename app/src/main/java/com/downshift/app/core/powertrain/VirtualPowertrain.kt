package com.downshift.app.core.powertrain

import com.downshift.app.core.build.TransmissionPart
import com.downshift.app.core.engine.EngineSpec
import com.downshift.app.core.model.DriveInputs
import com.downshift.app.core.model.GearState
import kotlin.math.abs
import kotlin.math.exp

enum class PowertrainEvent { STARTUP, SHUTDOWN, SHIFT_UP, SHIFT_DOWN, LIMITER, OVERRUN_START }

/** gear is 1.. in drive, 0 in park or neutral, -1 in reverse. load 0..1 is what the engine is asked for. */
data class PowertrainState(
    val running: Boolean,
    val rpm: Double,
    val load: Double,
    val gear: Int,
    val shifting: Boolean,
    val overrun: Boolean,
    val atLimiter: Boolean,
    val starting: Boolean,
) {
    companion object {
        val OFF = PowertrainState(false, 0.0, 0.0, 0, false, false, false, false)
    }
}

/**
 * A virtual engine and gearbox driven by the pedal, road speed and gear selector.
 * In park or neutral it free-revs with inertia; in drive the rpm follows the wheels through a gear it picks
 * itself, with launch slip in first gear and shifts shaped by the gearbox. Events from start() and stop()
 * come out of the next step.
 */
class VirtualPowertrain(var spec: EngineSpec, var gears: GearSet, var transmission: TransmissionPart) {
    var state: PowertrainState = PowertrainState.OFF
        private set

    private val pending = ArrayList<PowertrainEvent>(4)
    private var running = false
    private var startPending = false
    private var stopPending = false
    private var sinceStart = 0.0
    private var rpm = 0.0
    private var load = 0.0
    private var gear = 0
    private var lastSelector = GearState.UNKNOWN
    private var shiftLeft = 0.0
    private var shiftTotal = 0.0
    private var shiftUp = false
    private var cutLeft = 0.0
    private var sinceCut = Double.MAX_VALUE
    private var atLimiter = false
    private var overrun = false

    // Set by the mode step, read when the load is updated.
    private var loadScale = 1.0
    private var loadCut = false

    fun start() {
        if (running) return
        running = true
        startPending = true
        sinceStart = 0.0
    }

    fun stop() {
        if (!running) return
        running = false
        stopPending = true
    }

    fun step(inputs: DriveInputs, dtSec: Double): List<PowertrainEvent> {
        if (startPending) { pending += PowertrainEvent.STARTUP; startPending = false }
        if (stopPending) { pending += PowertrainEvent.SHUTDOWN; stopPending = false }
        val dt = dtSec.coerceAtLeast(0.0)
        if (running) runningStep(inputs, dt) else stoppedStep(dt)
        return if (pending.isEmpty()) emptyList() else pending.toList().also { pending.clear() }
    }

    private fun stoppedStep(dt: Double) {
        rpm = approach(rpm, 0.0, SHUTDOWN_TAU, dt).let { if (it < STALL_RPM) 0.0 else it }
        load = 0.0
        gear = 0
        shiftLeft = 0.0
        cutLeft = 0.0
        sinceCut = Double.MAX_VALUE
        atLimiter = false
        overrun = false
        lastSelector = GearState.UNKNOWN
        state = PowertrainState(false, rpm, 0.0, 0, false, false, false, false)
    }

    private fun runningStep(inputs: DriveInputs, dt: Double) {
        sinceStart += dt
        val locked = sinceStart < THROTTLE_LOCKOUT_SEC
        val throttle = if (locked) 0.0 else inputs.throttle.coerceIn(0.0, 1.0)
        val speed = abs(inputs.speedMps)
        val selector = if (inputs.gear == GearState.UNKNOWN) GearState.NEUTRAL else inputs.gear
        if (selector == GearState.DRIVE && lastSelector != GearState.DRIVE) {
            gear = gearFor(speed, throttle)
            shiftLeft = 0.0
        }
        lastSelector = selector
        loadScale = 1.0
        loadCut = false

        var target: Double
        var tau: Double
        when (selector) {
            GearState.DRIVE -> {
                gear = gear.coerceIn(1, gears.ratios.size)
                if (shiftLeft <= 0.0) maybeShift(speed, throttle)
                val coupled = coupledAt(gear, speed)
                if (shiftLeft > 0.0) {
                    val progress = 1.0 - shiftLeft / shiftTotal
                    target = maxOf(coupled, spec.idleRpm)
                    tau = shiftTotal / 2
                    when (transmission) {
                        TransmissionPart.AT -> loadScale = AT_SHIFT_LOAD
                        TransmissionPart.DCT -> if (shiftUp && progress < DCT_CUT_FRACTION) loadCut = true
                        TransmissionPart.MT_BLIP -> when {
                            // The virtual driver lifts while the clutch is in, and blips on the way down.
                            shiftUp && progress < MT_LIFT_FRACTION -> loadCut = true
                            !shiftUp && progress < MT_BLIP_FRACTION -> { target = coupled + BLIP_RPM; tau = TRACK_TAU }
                        }
                    }
                    shiftLeft -= dt
                } else {
                    target = if (gear == 1) launchTarget(coupled, speed, throttle) else maxOf(coupled, spec.idleRpm)
                    tau = TRACK_TAU
                }
            }
            GearState.REVERSE -> {
                gear = -1
                shiftLeft = 0.0
                target = launchTarget(coupledAt(1, speed), speed, throttle)
                tau = TRACK_TAU
            }
            else -> {
                gear = 0
                shiftLeft = 0.0
                target = spec.idleRpm + throttle * (spec.limiterRpm + FREE_REV_HEADROOM - spec.idleRpm)
                tau = if (target > rpm) spec.riseTau else spec.fallTau
            }
        }
        if (sinceStart < STARTUP_SEC && target < rpm) tau = maxOf(tau, spec.fallTau)
        if (cutLeft > 0.0) {
            target = spec.redlineRpm - LIMITER_DROP_RPM
            tau = TRACK_TAU
            cutLeft -= dt
        }

        rpm = if (sinceStart < FLARE_END_SEC) startupRpm(sinceStart) else approach(rpm, target, tau, dt)
        sinceCut += dt
        if (rpm >= spec.limiterRpm) {
            rpm = spec.limiterRpm
            cutLeft = LIMITER_CUT_SEC
            sinceCut = 0.0
        }
        val limiterNow = sinceCut < LIMITER_HOLD_SEC
        if (limiterNow && !atLimiter) pending += PowertrainEvent.LIMITER
        atLimiter = limiterNow

        val floor = if (selector == GearState.DRIVE || selector == GearState.REVERSE) {
            OVERRUN_DRIVE_FRACTION * spec.redlineRpm
        } else {
            spec.idleRpm + OVERRUN_FREE_FRACTION * (spec.redlineRpm - spec.idleRpm)
        }
        val overrunNow = !locked && throttle < COAST_THROTTLE && rpm > floor
        if (overrunNow && !overrun) pending += PowertrainEvent.OVERRUN_START
        overrun = overrunNow

        load = if (overrun || loadCut || cutLeft > 0.0) 0.0 else approach(load, throttle * loadScale, LOAD_TAU, dt)
        state = PowertrainState(true, rpm, load, gear, shiftLeft > 0.0, overrun, atLimiter, sinceStart < STARTUP_SEC)
    }

    private fun maybeShift(speed: Double, throttle: Double) {
        val redline = spec.redlineRpm
        val coupled = coupledAt(gear, speed)
        val coasting = throttle < COAST_THROTTLE
        val up = ShiftSchedule.upshiftRpm(throttle, redline)
        when {
            // Coasting only upshifts to save the engine, so lifting off does not walk up the gears.
            gear < gears.ratios.size && coupled >= up && (!coasting || rpm > COAST_UPSHIFT_FRACTION * redline) ->
                beginShift(gear + 1, true)
            gear > 1 && coupled <= ShiftSchedule.downshiftRpm(throttle, redline) -> beginShift(gear - 1, false)
            gear > 1 && throttle > KICKDOWN_THROTTLE && rpm < KICKDOWN_BELOW * redline &&
                coupledAt(gear - 1, speed) < minOf(KICKDOWN_CEILING * redline, KICKDOWN_MARGIN * up) -> beginShift(gear - 1, false)
        }
    }

    private fun beginShift(to: Int, up: Boolean) {
        gear = to
        shiftUp = up
        shiftTotal = gears.shiftMs / 1000.0
        shiftLeft = shiftTotal
        pending += if (up) PowertrainEvent.SHIFT_UP else PowertrainEvent.SHIFT_DOWN
    }

    /** The lowest gear that keeps the wheels below the upshift point, used when the selector enters drive. */
    private fun gearFor(speed: Double, throttle: Double): Int {
        val up = ShiftSchedule.upshiftRpm(throttle, spec.redlineRpm)
        var g = 1
        while (g < gears.ratios.size && coupledAt(g, speed) >= up) g++
        return g
    }

    /** First gear slips at low speed so the rpm rises before the car moves, fading out by SLIP_FADE_MPS. */
    private fun launchTarget(coupled: Double, speed: Double, throttle: Double): Double {
        val slip = spec.idleRpm + throttle * (LAUNCH_SLIP_FRACTION * spec.redlineRpm - spec.idleRpm)
        val w = (1.0 - speed / SLIP_FADE_MPS).coerceIn(0.0, 1.0)
        return maxOf(coupled, w * slip + (1 - w) * spec.idleRpm)
    }

    /** Cranking to 250 rpm, then a smooth flare to 1.6 × idle; the normal step lets it settle afterwards. */
    private fun startupRpm(t: Double): Double {
        if (t < CRANK_SEC) return CRANK_RPM * t / CRANK_SEC
        val x = ((t - CRANK_SEC) / (FLARE_END_SEC - CRANK_SEC)).coerceIn(0.0, 1.0)
        return CRANK_RPM + (FLARE_FACTOR * spec.idleRpm - CRANK_RPM) * x * x * (3 - 2 * x)
    }

    private fun coupledAt(g: Int, speed: Double) = coupledRpm(speed, gears.ratios[g - 1], gears.finalDrive)

    private fun approach(x: Double, target: Double, tau: Double, dt: Double): Double =
        if (tau <= 0.0) target else target + (x - target) * exp(-dt / tau)

    private companion object {
        const val CRANK_SEC = 0.25
        const val CRANK_RPM = 250.0
        const val FLARE_END_SEC = 0.45
        const val FLARE_FACTOR = 1.6
        const val THROTTLE_LOCKOUT_SEC = 0.6
        const val STARTUP_SEC = 1.8
        const val FREE_REV_HEADROOM = 300.0
        const val LIMITER_CUT_SEC = 0.05
        const val LIMITER_DROP_RPM = 400.0
        const val LIMITER_HOLD_SEC = 0.25
        const val TRACK_TAU = 0.05
        const val LOAD_TAU = 0.06
        const val LAUNCH_SLIP_FRACTION = 0.45
        const val SLIP_FADE_MPS = 8.0
        const val COAST_THROTTLE = 0.05
        const val COAST_UPSHIFT_FRACTION = 0.75
        const val KICKDOWN_THROTTLE = 0.85
        const val KICKDOWN_BELOW = 0.6
        const val KICKDOWN_CEILING = 0.88
        const val KICKDOWN_MARGIN = 0.95
        const val AT_SHIFT_LOAD = 0.7
        const val DCT_CUT_FRACTION = 0.5
        const val MT_LIFT_FRACTION = 0.6
        const val MT_BLIP_FRACTION = 0.4
        const val BLIP_RPM = 350.0
        const val OVERRUN_FREE_FRACTION = 0.35
        const val OVERRUN_DRIVE_FRACTION = 0.3
        const val SHUTDOWN_TAU = 0.25
        const val STALL_RPM = 50.0
    }
}
