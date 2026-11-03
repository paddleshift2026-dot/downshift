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
}
