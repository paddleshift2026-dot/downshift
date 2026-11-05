package com.downshift.app.core.garage

import kotlinx.serialization.Serializable

/** The 세팅 tab in steps: pops 0..5, redline offset ±500 rpm in hundreds, volume 0..100 in tens. */
@Serializable
data class Tune(val pops: Int = 3, val redlineOffset: Int = 0, val volume: Int = 80)

/** Everything the garage keeps: the balance, what is owned, what is fitted (by part id), the tune and the voice cards that are on. */
@Serializable
data class GarageState(
    val version: Int = 1,
    val balance: Int = 0,
    val owned: Set<String> = Catalog.BASE_IDS,
    val engine: String = "i4",
    val intake: String = "na",
    val exhaust: String = "stock",
    val transmission: String = "at",
    val tune: Tune = Tune(),
    /** The voice cards that are on (spec 7); only cards with a recording are heard (Garage.voicesHeard). */
    val voiceOn: Set<Category> = emptySet(),
) {
    companion object {
        /** A new driver: no points, the base build. */
        val FIRST_RUN = GarageState()

        /** The design's demo state: enough points to unlock V12 on stage. */
        val DEMO = GarageState(
            balance = 3000,
            owned = setOf("i4", "v8", "na", "t1", "t2", "stock", "sport", "at", "dct"),
            engine = "i4",
            intake = "t2",
            exhaust = "sport",
            transmission = "dct",
        )
    }
}
