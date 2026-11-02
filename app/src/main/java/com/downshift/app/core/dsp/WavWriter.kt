package com.downshift.app.core.dsp

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Writes mono floats as a 16-bit PCM WAV, for offline renders and in-app recordings. */
object WavWriter {
    fun pcm16Mono(samples: FloatArray, count: Int, sampleRate: Int): ByteArray {
        val data = count * 2
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + data).put("WAVE".toByteArray(Charsets.US_ASCII))
        b.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray(Charsets.US_ASCII)).putInt(data)
        for (i in 0 until count) b.putShort((samples[i].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
        return b.array()
    }
}
