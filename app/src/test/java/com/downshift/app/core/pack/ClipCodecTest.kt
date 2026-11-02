package com.downshift.app.core.pack

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ClipCodecTest {
    private val golden = "4453413144ac000004000000dae75c47aa8f89e495e71345"
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }

    @Test
    fun encodingMatchesTheSharedGoldenVector() = assertEquals(golden, hex(ClipCodec.encode("idle", 44_100, floatArrayOf(0f, 0.5f, -0.5f, 1f))))

    @Test
    fun decodingTheGoldenVectorGivesTheSamplesBack() {
        val w = ClipCodec.decode("idle", unhex(golden))
        assertEquals(44_100, w.sampleRate)
        assertArrayEquals(floatArrayOf(0f, 16384 / 32767f, -16383 / 32767f, 1f), w.mono, 1e-6f)
    }

    @Test
    fun roundTripKeepsSamplesWithinOneStep() {
        val x = FloatArray(1000) { ((it * 37) % 200 - 100) / 100f }
        assertArrayEquals(x, ClipCodec.decode("med_on", ClipCodec.encode("med_on", 44_100, x)).mono, 1f / 32767 + 1e-6f)
    }

    @Test
    fun thePayloadDoesNotLookLikePcm() {
        val x = FloatArray(64) { it / 64f }
        val enc = ClipCodec.encode("low_on", 44_100, x)
        val plainPrefix = ByteArray(16) { i -> val s = kotlin.math.floor(x[i / 2] * 32767.0 + 0.5).toInt(); (if (i % 2 == 0) s else s shr 8).toByte() }
        assertFalse(enc.copyOfRange(16, 32).contentEquals(plainPrefix))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aWrongNameFailsTheChecksum() {
        ClipCodec.decode("other", ClipCodec.encode("idle", 44_100, floatArrayOf(0.1f, 0.2f)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun somethingElseIsRejected() {
        ClipCodec.decode("idle", ByteArray(16))
    }
}
