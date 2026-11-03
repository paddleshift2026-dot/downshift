package com.downshift.app.core.garage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/** One engine's turntable: the frame count, the frame each tab turns to, and where each part sits in it (0..1 of the image). */
data class TurnSet(val frames: Int, val focus: Map<Category, Int>, val anchors: Map<Category, Pair<Double, Double>>)

/**
 * The 3D stage, done with pre-rendered frames: frame k looks from azimuth 35° − 15°·k. Dragging right turns the engine
 * right (the frame number goes down, as in the design), choosing a tab spins the short way to the frame that shows that
 * part, and the supercharger or twin turbos come as a second set of frames drawn over the plain engine.
 */
object Turntable {
    const val FRAMES = 24

    private val TURBOS = setOf("t1", "t2", "t3")
    private val KEYS = mapOf("intake" to Category.INTAKE, "exhaust" to Category.EXHAUST, "trans" to Category.TRANSMISSION)
    private val json = Json { ignoreUnknownKeys = true }

    /** The frame after dragging [dxPx] from [start], one frame per [pxPerFrame], wrapping around. */
    fun dragFrame(start: Int, dxPx: Float, pxPerFrame: Float, frames: Int = FRAMES): Int {
        if (pxPerFrame <= 0f) return start
        return (start - (dxPx / pxPerFrame).roundToInt()).mod(frames)
    }

    /** One frame from [cur] towards [target], the short way round. */
    fun spinStep(cur: Int, target: Int, frames: Int = FRAMES): Int {
        val d = (target - cur).mod(frames)
        return when {
            d == 0 -> cur
            d <= frames / 2 -> (cur + 1).mod(frames)
            else -> (cur - 1).mod(frames)
        }
    }

    /** Which render set shows this engine with this intake: v8sc and v12t are the overlays. */
    fun variant(engineId: String, intakeId: String): String = when {
        engineId == "v8" && intakeId == "sc" -> "v8sc"
        engineId == "v12" && intakeId in TURBOS -> "v12t"
        else -> engineId
    }

    /** Reads the render tool's anchors.json; the engine tab always faces frame 0. */
    fun parse(json: String): TurnSet {
        val r = this.json.decodeFromString(RenderJson.serializer(), json)
        val focus = mutableMapOf(Category.ENGINE to 0)
        val anchors = mutableMapOf<Category, Pair<Double, Double>>()
        for ((key, a) in r.anchors) {
            val c = KEYS[key] ?: continue
            focus[c] = a.k.mod(r.frames)
            anchors[c] = a.p[0] to a.p[1]
        }
        return TurnSet(r.frames, focus, anchors)
    }

    @Serializable
    private class AnchorJson(val k: Int, val p: List<Double>)

    @Serializable
    private class RenderJson(val frames: Int = FRAMES, val anchors: Map<String, AnchorJson> = emptyMap())
}
