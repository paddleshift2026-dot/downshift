package com.downshift.app.core.pack

import com.downshift.app.core.audio.WavData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.floor

/**
 * The .dsa clip format, shared with tools/pack_builder.py: "DSA1", sample rate, frame count and the CRC32 of the
 * plain PCM (all little-endian), then 16-bit PCM XORed with a keystream seeded by the clip name.
 * It keeps licensed recordings from being lifted out of the APK as WAVs; it is obfuscation, not DRM.
 */
object ClipCodec {
    private val MAGIC = "DSA1".toByteArray(Charsets.US_ASCII)
    private const val HEADER = 16

    fun encode(name: String, sampleRate: Int, mono: FloatArray): ByteArray {
        val plain = ByteArray(mono.size * 2)
        for (i in mono.indices) {
            val s = floor(mono[i].coerceIn(-1f, 1f) * 32767.0 + 0.5).toInt()
            plain[2 * i] = s.toByte()
            plain[2 * i + 1] = (s shr 8).toByte()
        }
        val crc = CRC32().apply { update(plain) }.value
        xorKeystream(name, plain)
        val out = ByteBuffer.allocate(HEADER + plain.size).order(ByteOrder.LITTLE_ENDIAN)
        out.put(MAGIC).putInt(sampleRate).putInt(mono.size).putInt(crc.toInt()).put(plain)
        return out.array()
    }

    /** Throws IllegalArgumentException for anything that is not a .dsa clip saved under this name. */
    fun decode(name: String, bytes: ByteArray): WavData {
        require(bytes.size >= HEADER && bytes.copyOfRange(0, 4).contentEquals(MAGIC)) { "not a .dsa clip" }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val rate = b.getInt(4)
        val frames = b.getInt(8)
        val crc = b.getInt(12).toLong() and 0xFFFFFFFFL
        require(rate > 0 && frames >= 0 && bytes.size.toLong() == HEADER + 2L * frames) { "corrupt .dsa header" }
        val plain = bytes.copyOfRange(HEADER, bytes.size)
        xorKeystream(name, plain)
        require(CRC32().apply { update(plain) }.value == crc) { "checksum mismatch: wrong clip name or damaged file" }
        val mono = FloatArray(frames) { i -> ((plain[2 * i].toInt() and 0xFF) or (plain[2 * i + 1].toInt() shl 8)).toShort() / 32767f }
        return WavData(rate, 1, mono)
    }

    /** xorshift32 seeded by the FNV-1a hash of the name, four little-endian bytes per step. */
    private fun xorKeystream(name: String, data: ByteArray) {
        var s = fnv1a(name).let { if (it == 0) 0x9E3779B9.toInt() else it }
        var i = 0
        while (i < data.size) {
            s = s xor (s shl 13)
            s = s xor (s ushr 17)
            s = s xor (s shl 5)
            for (k in 0 until minOf(4, data.size - i)) data[i + k] = (data[i + k].toInt() xor (s ushr (8 * k))).toByte()
            i += 4
        }
    }

    private fun fnv1a(text: String): Int {
        var h = 0x811C9DC5.toInt()
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            h = h xor (byte.toInt() and 0xFF)
            h *= 0x01000193
        }
        return h
    }
}
