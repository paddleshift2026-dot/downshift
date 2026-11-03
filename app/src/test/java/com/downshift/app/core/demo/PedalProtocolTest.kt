package com.downshift.app.core.demo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PedalProtocolTest {
    @Test
    fun pedalValuesAreWholePercentsInRange() {
        assertEquals(PageMessage.Pedal(42), PedalProtocol.parse("""{"t":"pedal","p":42}"""))
        assertEquals(PageMessage.Pedal(43), PedalProtocol.parse("""{"t":"pedal","p":42.6}"""))
        assertEquals(PageMessage.Pedal(0), PedalProtocol.parse("""{"t":"pedal","p":-5}"""))
        assertEquals(PageMessage.Pedal(100), PedalProtocol.parse("""{"t":"pedal","p":180}"""))
    }

    @Test
    fun aPingCarriesItsId() = assertEquals(PageMessage.Ping(7), PedalProtocol.parse("""{"t":"ping","id":7}"""))

    @Test
    fun onlyKnownScenesAreTaken() {
        assertEquals(PageMessage.Scene("school"), PedalProtocol.parse("""{"t":"scene","s":"school"}"""))
        assertNull(PedalProtocol.parse("""{"t":"scene","s":"add_points"}"""))
    }

    @Test
    fun anythingElseIsIgnored() {
        for (text in listOf("", "{", "[1,2]", "42", """{"t":"pedal"}""", """{"t":"pedal","p":"x"}""", """{"t":"jump"}""", """{"p":40}""")) {
            assertNull(text, PedalProtocol.parse(text))
        }
    }

    @Test
    fun theStateIsJsonThePageCanRead() {
        val text = PedalProtocol.state(PageState(rpm = 3120, kph = 42, gear = "D", screen = "DRIVE", engineOn = true, pct = 60))
        val o = Json.parseToJsonElement(text).jsonObject
        assertEquals("state", o["t"]!!.jsonPrimitive.content)
        assertEquals(3120, o["rpm"]!!.jsonPrimitive.int)
        assertEquals(42, o["kph"]!!.jsonPrimitive.int)
        assertEquals("D", o["gear"]!!.jsonPrimitive.content)
        assertEquals("DRIVE", o["screen"]!!.jsonPrimitive.content)
        assertEquals(true, o["engine"]!!.jsonPrimitive.boolean)
        assertEquals(60, o["pct"]!!.jsonPrimitive.int)
        assertEquals("""{"t":"pong","id":9}""", PedalProtocol.pong(9))
    }
}
