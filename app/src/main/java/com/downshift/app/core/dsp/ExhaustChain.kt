package com.downshift.app.core.dsp

import com.downshift.app.core.build.ExhaustPart

/**
 * The exhaust part as a tone: a bass shelf, a body peak, a treble shelf and saturation.
 * Stock is muffled and clean, sport fuller, track the boomiest and most driven.
 */
class ExhaustChain(private val sampleRate: Double, part: ExhaustPart) {
    private val filters: List<Biquad>
    val drive: Float

    init {
        val v = when (part) {
            ExhaustPart.STOCK -> Voice(150.0, 0.0, 400.0, 0.0, 4000.0, -5.0, 0.3f)
            ExhaustPart.SPORT -> Voice(150.0, 3.0, 450.0, 2.0, 5000.0, -1.0, 0.8f)
            ExhaustPart.TRACK -> Voice(140.0, 6.0, 500.0, 3.0, 6000.0, 1.0, 1.6f)
        }
        filters = listOf(
            Biquad.lowShelf(sampleRate, v.bassHz, v.bassDb),
            Biquad.peaking(sampleRate, v.bodyHz, v.bodyDb, q = 1.0),
            Biquad.highShelf(sampleRate, v.trebleHz, v.trebleDb),
        )
        drive = v.drive
    }

    fun gainDb(freqHz: Double): Double = filters.sumOf { it.gainDb(freqHz, sampleRate) }

    fun process(buf: FloatArray, n: Int) {
        val (bass, body, treble) = filters
        for (i in 0 until n) buf[i] = softClip(treble.process(body.process(bass.process(buf[i]))), drive)
    }

    private class Voice(
        val bassHz: Double, val bassDb: Double,
        val bodyHz: Double, val bodyDb: Double,
        val trebleHz: Double, val trebleDb: Double,
        val drive: Float,
    )
}
