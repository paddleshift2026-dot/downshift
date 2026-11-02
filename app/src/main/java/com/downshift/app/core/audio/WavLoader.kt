package com.downshift.app.core.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavData(val sampleRate: Int, val channels: Int, val mono: FloatArray)

/** Reads 16- or 24-bit PCM WAV (RIFF) into mono floats in -1..1; stereo is averaged. Anything else is rejected. */
object WavLoader {
    fun load(bytes: ByteArray): WavData {
        require(bytes.size >= 12 && tag(bytes, 0) == "RIFF" && tag(bytes, 8) == "WAVE") { "not a RIFF/WAVE file" }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var format = 0
        var channels = 0
        var rate = 0
        var bits = 0
        var dataStart = -1
        var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = tag(bytes, pos)
            val body = pos + 8
            val len = b.getInt(pos + 4).let { if (it < 0 || body + it > bytes.size) bytes.size - body else it }
            when (id) {
                "fmt " -> {
                    format = b.getShort(body).toInt()
                    channels = b.getShort(body + 2).toInt()
                    rate = b.getInt(body + 4)
                    bits = b.getShort(body + 14).toInt()
                }
                "data" -> {
                    dataStart = body
                    dataLen = len
                }
            }
            pos = body + len + (len and 1)
        }
        require(format == 1 && (bits == 16 || bits == 24)) { "only 16/24-bit PCM is supported (format=$format bits=$bits)" }
        require(channels in 1..2 && dataStart >= 0) { "missing fmt/data chunk" }
        val bytesPer = bits / 8
        val frames = dataLen / (bytesPer * channels)
        val mono = FloatArray(frames)
        for (f in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) {
                val o = dataStart + (f * channels + c) * bytesPer
                sum += if (bits == 16) {
                    b.getShort(o) / 32768f
                } else {
                    ((bytes[o].toInt() and 0xFF) or ((bytes[o + 1].toInt() and 0xFF) shl 8) or (bytes[o + 2].toInt() shl 16)) / 8388608f
                }
            }
            mono[f] = sum / channels
        }
        return WavData(rate, channels, mono)
    }

    private fun tag(bytes: ByteArray, at: Int) = String(bytes, at, 4, Charsets.US_ASCII)
}
