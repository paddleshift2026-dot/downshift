package com.downshift.app.core.trip

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/** One sample of the car while a trip runs: speed, the turn-signal stalk and the road's speed limit (0 unknown). */
data class TripTick(val speedMps: Double, val signal: Signal, val limitKph: Int)

/** What a trip has added up to; saved in checkpoints, so a restarted app can carry on. */
@Serializable
data class TripTotals(
    val distanceKm: Double = 0.0,
    val knownKm: Double = 0.0,
    val keptKm: Double = 0.0,
    val unknownKm: Double = 0.0,
    val placesPassed: Int = 0,
    val placesKept: Int = 0,
    val turns: Int = 0,
    val signalled: Int = 0,
) {
    operator fun plus(o: TripTotals) = TripTotals(
        distanceKm + o.distanceKm, knownKm + o.knownKm, keptKm + o.keptKm, unknownKm + o.unknownKm,
        placesPassed + o.placesPassed, placesKept + o.placesKept, turns + o.turns, signalled + o.signalled,
    )
}

/** A trip's points by kind; each is rounded on its own so the summary's rows add up to the total. */
data class TripPoints(val distance: Int, val limit: Int, val places: Int, val signals: Int) {
    val total: Int get() = distance + limit + places + signals
}

/** The PRD's safe-driving points. Nothing is ever taken away. */
object PointRules {
    const val PER_KM = 5.0
    const val PER_KEPT_KM = 1.0
    const val PER_PLACE = 6
    const val PER_SIGNAL = 0.5
    const val SUMMARY_MIN_KM = 0.1

    fun of(t: TripTotals) = TripPoints(
        distance = (t.distanceKm * PER_KM).roundToInt(),
        limit = (t.keptKm * PER_KEPT_KM).roundToInt(),
        places = t.placesKept * PER_PLACE,
        signals = (t.signalled * PER_SIGNAL).roundToInt(),
    )

    /** A hop shorter than this (moving out of a parking space) adds its points without a summary. */
    fun showsSummary(t: TripTotals) = t.distanceKm >= SUMMARY_MIN_KM
}

/**
 * One trip: distance by speed limit, school zones and cameras, turn signals. [start] holds what a checkpoint saved
 * when the trip carries on after the app was restarted; totals() adds this run to it.
 */
class TripMeter(private val start: TripTotals = TripTotals()) {
    private val distance = DistanceMeter()
    private val places = PlaceJudge()
    private val turnJudge = TurnJudge()

    val schoolZone: Boolean get() = places.schoolZone

    fun step(t: TripTick, dtSec: Double) {
        distance.step(t.speedMps, t.limitKph, dtSec)
        places.step(t.speedMps, dtSec)
        turnJudge.step(t.speedMps, t.signal, dtSec)
    }

    fun odometer(km: Double) = distance.odometer(km)

    /** The demo's distance lapse: the speed integral (multiplied) is the distance from here on. */
    fun ignoreOdometer() = distance.ignoreOdometer()

    fun turn(t: TurnAhead) = turnJudge.announce(t)

    fun place(p: PlaceAhead) = places.announce(p)

    fun totals(): TripTotals = start + TripTotals(
        distance.totalKm, distance.knownKm, distance.keptKm, distance.unknownKm,
        places.passed, places.kept, turnJudge.turns, turnJudge.signalled,
    )

    fun finish(): TripTotals {
        places.finish()
        return totals()
    }
}

/** A running trip as saved every few seconds; targetId is the part the drive screen's ring fills toward. */
@Serializable
data class TripCheckpoint(
    val version: Int = 1,
    val startedAtMs: Long,
    val savedAtMs: Long,
    val targetId: String? = null,
    val totals: TripTotals = TripTotals(),
)

/** Checkpoints as JSON. Nothing saved or a broken value reads as no trip. */
object TripCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(c: TripCheckpoint): String = json.encodeToString(TripCheckpoint.serializer(), c)

    fun decode(json: String?): TripCheckpoint? {
        if (json.isNullOrBlank()) return null
        return try {
            this.json.decodeFromString(TripCheckpoint.serializer(), json)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
