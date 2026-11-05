package com.downshift.app.adapters.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.downshift.app.core.audio.WavLoader
import com.downshift.app.core.voice.VoiceCut
import java.io.File
import kotlin.math.abs

/** Where a take's samples come from: the car's microphone, or a WAV file standing in for it (adb, the demo video). */
interface MicSource {
    /** False when the microphone cannot be had (no permission, another app holds it). */
    fun open(rate: Int): Boolean

    /** Fills [buf] like a live microphone (blocking); returns how many samples, or -1 when it broke. */
    fun read(buf: FloatArray): Int
    fun close()
}

/** The microphone through AudioRecord: VOICE_RECOGNITION (no automatic gain control), MIC when that is refused. */
class AndroidMic : MicSource {
    private var rec: AudioRecord? = null
    private var shorts = ShortArray(0)

    @SuppressLint("MissingPermission") // the record sheet asks for RECORD_AUDIO before the first take
    override fun open(rate: Int): Boolean {
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        for (source in intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            val r = runCatching { AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate / 5) * 2) }.getOrNull()
            if (r != null && r.state == AudioRecord.STATE_INITIALIZED) {
                r.startRecording()
                if (r.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    rec = r
                    return true
                }
            }
            r?.release()
        }
        return false
    }

    override fun read(buf: FloatArray): Int {
        val r = rec ?: return -1
        if (shorts.size < buf.size) shorts = ShortArray(buf.size)
        val n = r.read(shorts, 0, buf.size)
        if (n < 0) return -1
        for (i in 0 until n) buf[i] = shorts[i] / 32768f
        return n
    }

    override fun close() {
        rec?.run {
            runCatching { stop() }
            release()
        }
        rec = null
    }
}

/** A WAV file fed at the pace of a live microphone, so the level bar moves as in a real take; silence after its end. */
class FileMic(private val file: File) : MicSource {
    private var data = FloatArray(0)
    private var pos = 0
    private var rate = 1
    private var startNs = 0L

    override fun open(rate: Int): Boolean = runCatching {
        val w = WavLoader.load(file.readBytes())
        data = if (w.sampleRate == rate) w.mono else VoiceCut.resample(w.mono, w.sampleRate, rate)
        this.rate = rate
        pos = 0
        startNs = System.nanoTime()
        true
    }.getOrDefault(false)

    override fun read(buf: FloatArray): Int {
        val due = startNs + (pos + buf.size).toLong() * 1_000_000_000L / rate
        val waitMs = (due - System.nanoTime()) / 1_000_000
        if (waitMs > 0) Thread.sleep(waitMs)
        val n = (data.size - pos).coerceIn(0, buf.size)
        if (n > 0) System.arraycopy(data, pos, buf, 0, n)
        java.util.Arrays.fill(buf, n, buf.size, 0f)
        pos += buf.size
        return buf.size
    }

    override fun close() = Unit
}

/** One take: [seconds] from a source in 50 ms chunks, each chunk's peak sent to the level bar. */
class MicRecorder(private val source: () -> MicSource) {
    sealed class Result {
        class Done(val samples: FloatArray) : Result()
        object Cancelled : Result()
        object Failed : Result()
    }

    /** Blocking: call off the main thread. Ends as Cancelled as soon as [keepGoing] is false (the car moved). */
    fun record(seconds: Double, rate: Int, keepGoing: () -> Boolean, level: (Float) -> Unit): Result {
        val mic = source()
        if (!mic.open(rate)) return Result.Failed
        try {
            val total = (seconds * rate).toInt()
            val out = FloatArray(total)
            val chunk = FloatArray(rate / 20)
            var got = 0
            while (got < total) {
                if (!keepGoing()) return Result.Cancelled
                val n = mic.read(chunk)
                if (n < 0) return Result.Failed
                val take = minOf(n, total - got)
                System.arraycopy(chunk, 0, out, got, take)
                got += take
                var peak = 0f
                for (i in 0 until take) peak = maxOf(peak, abs(chunk[i]))
                level(peak)
            }
            return Result.Done(out)
        } finally {
            mic.close()
        }
    }
}
