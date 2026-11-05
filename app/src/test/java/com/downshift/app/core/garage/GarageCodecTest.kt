package com.downshift.app.core.garage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GarageCodecTest {
    @Test
    fun aStateSurvivesTheRoundTrip() = assertEquals(GarageState.DEMO, GarageCodec.decode(GarageCodec.encode(GarageState.DEMO)))

    @Test
    fun nothingOrGarbageStartsFresh() {
        assertEquals(GarageState.FIRST_RUN, GarageCodec.decode(null))
        assertEquals(GarageState.FIRST_RUN, GarageCodec.decode("{not json"))
    }

    @Test
    fun unknownOrUnownedPartsAreCleanedUp() {
        val s = GarageCodec.decode("""{"version":1,"balance":-5,"owned":["v8","zz"],"engine":"zz","intake":"t3","exhaust":"stock","transmission":"at","tune":{"pops":7}}""")
        assertEquals(0, s.balance)
        assertFalse("zz" in s.owned)
        assertTrue(s.owned.containsAll(Catalog.BASE_IDS + "v8"))
        assertEquals("i4", s.engine)
        assertEquals("na", s.intake) // t3 is not owned
        assertEquals(5, s.tune.pops)
    }

    @Test
    fun aDemoWalletThatWasNeverSavedStartsAsTheDemoState() {
        assertEquals(GarageState.DEMO, GarageCodec.decode(null, fallback = GarageState.DEMO))
        assertEquals(GarageState.DEMO, GarageCodec.decode("", fallback = GarageState.DEMO))
        assertEquals(GarageState.DEMO, GarageCodec.decode("{nope", fallback = GarageState.DEMO))
    }

    @Test
    fun aSavedDemoWalletIsReadLikeAnyOther() {
        val s = GarageState.DEMO.copy(balance = 600, owned = GarageState.DEMO.owned + "v12", engine = "v12")
        assertEquals(s, GarageCodec.decode(GarageCodec.encode(s), fallback = GarageState.DEMO))
    }

    @Test
    fun anOldSaveHasNoVoicesOn() = assertEquals(emptySet<Category>(), GarageCodec.decode("""{"version":1,"balance":10}""").voiceOn)

    @Test
    fun voiceCardsSurviveTheRoundTrip() {
        val s = GarageState.DEMO.copy(voiceOn = setOf(Category.ENGINE, Category.TRANSMISSION))
        assertEquals(s, GarageCodec.decode(GarageCodec.encode(s)))
    }
}
