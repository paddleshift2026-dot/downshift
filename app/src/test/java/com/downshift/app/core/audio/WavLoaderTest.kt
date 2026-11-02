package com.downshift.app.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavLoaderTest {
    private fun wav(channels: Int, rate: Int, bits: Int, samples: IntArray): ByteArray {
        val bytesPer = bits / 8
        val data = ByteArray(samples.size * bytesPer)
        samples.forEachIndexed { i, s -> for (k in 0 until bytesPer) data[i * bytesPer + k] = (s shr (8 * k)).toByte() }
        return ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(channels.toShort()); putInt(rate)
            putInt(rate * channels * bytesPer); putShort((channels * bytesPer).toShort()); putShort(bits.toShort())
            put("data".toByteArray()); putInt(data.size); put(data)
        }.array()
    }

    @Test
    fun monoSixteenBitSamplesBecomeFloats() {
        val w = WavLoader.load(wav(1, 48_000, 16, intArrayOf(0, 16384, -32768)))
        assertEquals(48_000, w.sampleRate)
        assertArrayEquals(floatArrayOf(0f, 0.5f, -1f), w.mono, 1e-6f)
    }

    @Test
    fun stereoIsAveragedToMono() {
        val w = WavLoader.load(wav(2, 44_100, 16, intArrayOf(16384, 0, -16384, -16384)))
        assertEquals(2, w.channels)
        assertArrayEquals(floatArrayOf(0.25f, -0.5f), w.mono, 1e-6f)
    }

    @Test
    fun twentyFourBitSamplesAreScaled() {
        val w = WavLoader.load(wav(1, 48_000, 24, intArrayOf(0x400000, -0x800000)))
        assertArrayEquals(floatArrayOf(0.5f, -1f), w.mono, 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonWavIsRejected() {
        WavLoader.load(ByteArray(64))
    }
}
