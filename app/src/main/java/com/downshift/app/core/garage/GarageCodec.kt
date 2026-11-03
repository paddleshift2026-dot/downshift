package com.downshift.app.core.garage

import kotlinx.serialization.json.Json

/**
 * The saved garage as one JSON value. Reading never fails: nothing saved or a broken value is a first run, and what no
 * longer makes sense is put right (unknown parts dropped, a fitted part that is not owned back to the base part,
 * a negative balance to 0, the tune into its range). The base parts are always owned.
 */
object GarageCodec {
    private const val VERSION = 1
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(s: GarageState): String = json.encodeToString(GarageState.serializer(), s)

    /** [fallback] is what nothing saved or a broken value reads as: a first run, or the demo state for the demo wallet. */
    fun decode(json: String?, fallback: GarageState = GarageState.FIRST_RUN): GarageState {
        if (json.isNullOrBlank()) return fallback
        val raw = try {
            this.json.decodeFromString(GarageState.serializer(), json)
        } catch (e: IllegalArgumentException) {
            return fallback
        }
        return clean(raw)
    }

    private fun clean(s: GarageState): GarageState {
        val owned = s.owned.filter { Catalog.part(it)?.kind == PartKind.NORMAL }.toSet() + Catalog.BASE_IDS
        fun slot(c: Category, id: String): String {
            val p = Catalog.part(id)
            return if (p != null && p.category == c && id in owned) id else Catalog.baseOf(c)
        }
        val fixed = s.copy(
            version = VERSION,
            balance = s.balance.coerceAtLeast(0),
            owned = owned,
            engine = slot(Category.ENGINE, s.engine),
            intake = slot(Category.INTAKE, s.intake),
            exhaust = slot(Category.EXHAUST, s.exhaust),
            transmission = slot(Category.TRANSMISSION, s.transmission),
        )
        return Garage.tune(fixed, fixed.tune)
    }
}
