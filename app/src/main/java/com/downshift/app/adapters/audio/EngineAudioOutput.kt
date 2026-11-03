package com.downshift.app.adapters.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import com.downshift.app.core.audio.LatencyStats
import com.downshift.app.core.dsp.WavWriter
import com.downshift.app.core.model.DriveInputs
import com.downshift.app.core.runtime.EngineRuntime
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Plays an EngineRuntime through a low-latency AudioTrack: each burst, the audio thread takes the newest inputs
 * (inputs() gets the block's length in seconds), renders one block and writes it, so a pedal change is heard about
 * two bursts later. Audio focus is never requested (M0: playback is not muted without it) and the usage is GAME.
 * inputAtMs() is when the pedal value in use arrived (0 if unknown); it feeds the latency statistics.
 */
class EngineAudioOutput(
    context: Context,
    private val runtime: EngineRuntime,
    private val inputs: (blockSec: Double) -> DriveInputs,
    private val inputAtMs: () -> Long = { 0L },
) {
    private val tag = "DownshiftAudio"
    private val audio = context.getSystemService(AudioManager::class.java)
    val latency = LatencyStats()
    @Volatile private var running = false
    private var thread: Thread? = null

    @Volatile var bufferInfo: String = "재생 안 함"
        private set

    // Recording: a ring allocated by startRecording and filled by the audio thread.
    @Volatile private var ring: FloatArray? = null
    @Volatile private var ringWritten = 0L

    fun start() {
        if (running) return
        val rate = runtime.sampleRate
        val burst = audio.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 256
        val minBytes = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(maxOf(minBytes, 2 * burst * 4))
            .build()
        track.setBufferSizeInFrames(2 * burst)
        val bufferMs = track.bufferSizeInFrames * 1000.0 / rate
        bufferInfo = "rate=$rate burst=$burst buffer=${track.bufferSizeInFrames}f(~${"%.0f".format(bufferMs)}ms) perf=${track.performanceMode}"
        Log.i(tag, "AudioTrack $bufferInfo")
        running = true
        track.play()
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buf = FloatArray(burst)
            val blockSec = burst.toDouble() / rate
            var lastThrottle = Double.NaN
            try {
                while (running) {
                    val inp = inputs(blockSec)
                    runtime.renderBlock(inp, buf, burst)
                    record(buf, burst)
                    track.write(buf, 0, burst, AudioTrack.WRITE_BLOCKING)
                    if (lastThrottle.isNaN() || abs(inp.throttle - lastThrottle) >= PEDAL_STEP) {
                        val now = System.currentTimeMillis()
                        if (!lastThrottle.isNaN()) {
                            Log.i(tag, "pedal applied v=${(inp.throttle * 100).roundToInt()} at=$now")
                            val at = inputAtMs()
                            if (at > 0) latency.add(now - at + bufferMs)
                        }
                        lastThrottle = inp.throttle
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "engine thread: ${e.message}")
            } finally {
                runCatching { track.stop() }
                track.release()
            }
        }, "DownshiftEngine").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        bufferInfo = "재생 안 함"
    }

    /** Keeps the last maxSec seconds of the mix until stopRecording. */
    fun startRecording(maxSec: Int = 60) {
        ringWritten = 0L
        ring = FloatArray(maxSec * runtime.sampleRate)
    }

    /** Saves what was recorded as a WAV and returns its length in seconds (0 if nothing was recording). */
    fun stopRecording(file: File): Double {
        val r = ring ?: return 0.0
        ring = null
        val n = minOf(ringWritten, r.size.toLong()).toInt()
        val start = if (ringWritten > r.size) (ringWritten % r.size).toInt() else 0
        val ordered = FloatArray(n) { r[(start + it) % r.size] }
        file.writeBytes(WavWriter.pcm16Mono(ordered, n, runtime.sampleRate))
        Log.i(tag, "recorded ${"%.1f".format(n.toDouble() / runtime.sampleRate)}s -> ${file.absolutePath}")
        return n.toDouble() / runtime.sampleRate
    }

    private fun record(buf: FloatArray, n: Int) {
        val r = ring ?: return
        var w = ringWritten
        for (i in 0 until n) {
            r[(w % r.size).toInt()] = buf[i]
            w++
        }
        ringWritten = w
    }

    private companion object {
        const val PEDAL_STEP = 0.01
    }
}
