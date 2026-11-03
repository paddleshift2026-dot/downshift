package com.downshift.app.core.situation

import kotlin.math.roundToInt

/** Sound that must come through the engine: navigation guidance, a call, an alarm. */
enum class PriorityAudio { NONE, GUIDANCE, CALL, ALERT }

/** Why the engine sounds the way it does, for the drive screen's chip. */
enum class Reason { NORMAL, OFF, MUTED, GUIDANCE, CALL, ALERT, SCHOOL_ZONE, QUIET_ZONE, PASSENGER }

data class SituationInputs(
    val running: Boolean = true,
    val muted: Boolean = false,
    val priority: PriorityAudio = PriorityAudio.NONE,
    val schoolZone: Boolean = false,
    val quietZone: Boolean = false,
    val passenger: Boolean = false,
    val userVolume: Double = 0.8,
)

/** gain multiplies the engine's own volume; volumePct is what that leaves of full scale. */
data class Situation(val gain: Double, val reason: Reason, val volumePct: Int)

/**
 * Situation-aware volume (PRD feature E). Guidance, calls and alarms duck the engine and keep it ducked for HOLD_SEC
 * after they stop, so it does not pump between phrases; school zones and quiet zones turn it down; a passenger caps
 * it at PASSENGER_VOLUME; mute silences it. Reductions multiply, and the chip names the one that matters most.
 */
class SoundSituation {
    private var held = PriorityAudio.NONE
    private var holdSec = 0.0

    fun step(i: SituationInputs, dtSec: Double): Situation {
        if (i.priority != PriorityAudio.NONE) {
            held = i.priority
            holdSec = HOLD_SEC
        } else if (holdSec > 0) {
            holdSec -= dtSec
            if (holdSec <= 0) held = PriorityAudio.NONE
        }
        val priority = if (i.priority != PriorityAudio.NONE) i.priority else held
        val passengerGain = if (i.passenger && i.userVolume > PASSENGER_VOLUME) PASSENGER_VOLUME / i.userVolume else 1.0
        val zoneGain = if (i.schoolZone || i.quietZone) ZONE_GAIN else 1.0
        val priorityGain = if (priority != PriorityAudio.NONE) PRIORITY_GAIN else 1.0
        val gain = if (i.muted) 0.0 else passengerGain * zoneGain * priorityGain
        val reason = when {
            i.muted -> Reason.MUTED
            !i.running -> Reason.OFF
            priority == PriorityAudio.GUIDANCE -> Reason.GUIDANCE
            priority == PriorityAudio.CALL -> Reason.CALL
            priority == PriorityAudio.ALERT -> Reason.ALERT
            i.schoolZone -> Reason.SCHOOL_ZONE
            i.quietZone -> Reason.QUIET_ZONE
            passengerGain < 1.0 -> Reason.PASSENGER
            else -> Reason.NORMAL
        }
        return Situation(gain, reason, (i.userVolume * gain * 100).roundToInt())
    }

    companion object {
        const val PRIORITY_GAIN = 0.25 // about -12 dB under guidance, calls and alarms
        const val ZONE_GAIN = 0.3 // about -10 dB in school zones and quiet zones
        const val PASSENGER_VOLUME = 0.4
        const val HOLD_SEC = 0.8
    }
}
