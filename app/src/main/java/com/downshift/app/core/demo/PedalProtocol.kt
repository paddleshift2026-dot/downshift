package com.downshift.app.core.demo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/** What the web pedal page sends: the pedal in whole percent, a keep-alive ping, or one of the presenter's scenes. */
sealed class PageMessage {
    data class Pedal(val pct: Int) : PageMessage()
    data class Ping(val id: Long) : PageMessage()
    data class Scene(val name: String) : PageMessage()
}

/** What the page shows about the car, five times a second while it is connected. */
data class PageState(val rpm: Int, val kph: Int, val gear: String, val screen: String, val engineOn: Boolean, val pct: Int)

/**
 * The web pedal's messages, one small JSON object each way: {"t":"pedal","p":0..100}, {"t":"ping","id":n} and
 * {"t":"scene","s":name} from the page; {"t":"state",…} and {"t":"pong","id":n} back. Anything else is dropped.
 */
object PedalProtocol {
    /** The presenter's buttons: gear D, park on arrival, a school zone ahead, a guidance prompt, a passenger in or out. */
    val SCENES = setOf("drive", "arrive", "school", "guide", "passenger_on", "passenger_off")

    fun parse(text: String): PageMessage? {
        val o = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        return when (o.string("t")) {
            "pedal" -> o.number("p")?.let { PageMessage.Pedal(it.roundToInt().coerceIn(0, 100)) }
            "ping" -> o.number("id")?.let { PageMessage.Ping(it.toLong()) }
            "scene" -> o.string("s")?.takeIf { it in SCENES }?.let { PageMessage.Scene(it) }
            else -> null
        }
    }

    fun state(s: PageState): String = buildJsonObject {
        put("t", "state")
        put("rpm", s.rpm)
        put("kph", s.kph)
        put("gear", s.gear)
        put("screen", s.screen)
        put("engine", s.engineOn)
        put("pct", s.pct)
    }.toString()

    fun pong(id: Long): String = buildJsonObject {
        put("t", "pong")
        put("id", id)
    }.toString()

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
}
