package com.downshift.app.core.garage

import com.downshift.app.core.build.EngineId
import com.downshift.app.core.build.ExhaustPart
import com.downshift.app.core.build.IntakePart
import com.downshift.app.core.build.TransmissionPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GarageTest {
    private val demo = GarageState.DEMO

    private fun price(id: String) = Catalog.part(id)!!.price

    @Test
    fun pricesMatchThePrd() {
        assertEquals(listOf(0, 800, 2400), listOf("i4", "v8", "v12").map(::price))
        assertEquals(listOf(0, 300, 600, 1500, 900), listOf("na", "t1", "t2", "t3", "sc").map(::price))
        assertEquals(listOf(0, 400, 1200), listOf("stock", "sport", "track").map(::price))
        assertEquals(listOf(0, 500, 1000), listOf("at", "dct", "mt").map(::price))
    }

    @Test
    fun theFirstRunOwnsAndWearsOnlyTheBaseParts() {
        val s = GarageState.FIRST_RUN
        assertEquals(0, s.balance)
        assertEquals(Catalog.BASE_IDS, s.owned)
        val b = Garage.build(s)
        assertEquals(listOf(EngineId.I4, IntakePart.NA, ExhaustPart.STOCK, TransmissionPart.AT), listOf(b.engine, b.intake, b.exhaust, b.transmission))
    }

    @Test
    fun statusFollowsOwnershipAndBalance() {
        fun st(s: GarageState, id: String) = Garage.status(s, Catalog.part(id)!!)
        assertEquals(PartStatus.EQUIPPED, st(demo, "i4"))
        assertEquals(PartStatus.OWNED, st(demo, "v8"))
        assertEquals(PartStatus.UNLOCKABLE, st(demo, "v12"))
        assertEquals(PartStatus.PLACEHOLDER, st(demo, "e4"))
        assertEquals(PartStatus.LIMITED, st(demo, "ltd"))
        val poor = demo.copy(balance = 1000)
        assertEquals(PartStatus.LOCKED, st(poor, "v12"))
        assertEquals(PartStatus.UNLOCKABLE, st(poor, "mt")) // exactly enough
    }

    @Test
    fun unlockingSpendsThePointsOnce() {
        val s = Garage.unlock(demo, "v12")!!
        assertEquals(600, s.balance)
        assertTrue("v12" in s.owned)
        assertNull(Garage.unlock(s, "v12"))
        assertNull(Garage.unlock(demo.copy(balance = 100), "v12"))
        assertNull(Garage.unlock(demo, "e4"))
        assertNull(Garage.unlock(demo, "ltd"))
    }

    @Test
    fun equippingNeedsOwnershipAndChangesTheBuild() {
        assertNull(Garage.equip(demo, "v12"))
        val s = Garage.equip(Garage.unlock(demo, "v12")!!, "v12")!!
        assertEquals(EngineId.V12, Garage.build(s).engine)
        assertEquals("v12", s.engine)
    }

    @Test
    fun previewSwapsOnePartAndLeavesTheStateAlone() {
        assertEquals(EngineId.V12, Garage.preview(demo, "v12").engine)
        assertEquals(IntakePart.TURBO2, Garage.preview(demo, "v12").intake)
        assertEquals("i4", demo.engine)
        assertEquals(Garage.build(demo), Garage.preview(demo, "e4"))
    }

    @Test
    fun thePlateNamesTheBuild() = assertEquals("I4 · TURBO 2 · SPORT · DCT", Garage.plate(Garage.build(demo)))

    @Test
    fun kmRoundsUpAndNeverSaysZero() {
        assertEquals(34, Garage.kmFor(255))
        assertEquals(11, Garage.kmFor(76))
        assertEquals(1, Garage.kmFor(1))
        assertEquals(1, Garage.kmFor(0))
    }

    @Test
    fun theNextTargetIsTheCheapestPartOutOfReach() {
        assertEquals("track", Garage.nextTarget(demo.copy(balance = 1000))!!.id)
        assertEquals("v12", Garage.nextTarget(demo)!!.id) // all affordable: the priciest left
        val everything = Catalog.of(Category.ENGINE) + Catalog.of(Category.INTAKE) + Catalog.of(Category.EXHAUST) + Catalog.of(Category.TRANSMISSION)
        assertNull(Garage.nextTarget(demo.copy(owned = everything.map { it.id }.toSet())))
    }

    @Test
    fun tuneStepsMapOntoTheBuildAndAreClamped() {
        val s = Garage.tune(demo, Tune(pops = 9, redlineOffset = 730, volume = 60))
        assertEquals(Tune(5, 500, 60), s.tune)
        val b = Garage.build(s)
        assertEquals(1.0, b.pops, 1e-9)
        assertEquals(500, b.redlineOffset)
        assertEquals(0.6, b.volume, 1e-9)
    }
}
