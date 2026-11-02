package com.downshift.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FuelKindTest {
    @Test
    fun electricOnlyIsAnEv() = assertEquals(FuelKind.EV, FuelKind.from(intArrayOf(10)))

    @Test
    fun petrolPlusElectricIsAHybrid() = assertEquals(FuelKind.HYBRID, FuelKind.from(intArrayOf(1, 10)))

    @Test
    fun petrolOrDieselOnlyIsCombustion() {
        assertEquals(FuelKind.ICE, FuelKind.from(intArrayOf(1)))
        assertEquals(FuelKind.ICE, FuelKind.from(intArrayOf(3)))
    }

    @Test
    fun missingOrUnknownStaysUnknown() {
        assertEquals(FuelKind.UNKNOWN, FuelKind.from(null))
        assertEquals(FuelKind.UNKNOWN, FuelKind.from(intArrayOf()))
        assertEquals(FuelKind.UNKNOWN, FuelKind.from(intArrayOf(0)))
    }
}
