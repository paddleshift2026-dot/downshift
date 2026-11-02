package com.downshift.app.adapters.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import com.downshift.app.core.audio.LatencyStats
import com.downshift.app.core.audio.LoopResampler

/** Low-latency AudioTrack loop whose pitch and level follow a virtual pedal; records pedal-to-output latency samples. */
class LoopPlayer(context: Context, private val stats: LatencyStats) {
    private val tag = "DownshiftAudio"
    private val audio = context.getSystemService(AudioManager::class.java)
    @Volatile private var throttle = 0.0
    @Volatile private var pendingSinceNs = 0L
    @Volatile private var running = false
    private var thread: Thread? = null
    private var lastLoop: FloatArray? = null
    private var lastRate = 48_000

    @Volatile var bufferInfo: String = "재생 안 함"
        private set

    fun start(loop: FloatArray, sampleRate: Int, usage: Int = AudioAttributes.USAGE_GAME) {
        stop()
        lastLoop = loop
        lastRate = sampleRate
        val burst = audio.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 256
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(minBytes)
            .build()
        val resampler = LoopResampler(loop)
        val buf = FloatArray(burst)
        val bufferMs = track.bufferSizeInFrames * 1000.0 / sampleRate
        bufferInfo = "rate=$sampleRate usage=$usage burst=$burst buffer=${track.bufferSizeInFrames}f(~${"%.0f".format(bufferMs)}ms) perf=${track.performanceMode}"
        Log.i(tag, "AudioTrack $bufferInfo")
        running = true
        track.play()
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            var gain = 0f
            try {
                while (running) {
                    val t = throttle
                    val since = pendingSinceNs
                    resampler.rate = 1.0 + 3.0 * t
                    val target = (0.25 + 0.75 * t).toFloat()
                    resampler.render(buf)
                    for (i in buf.indices) {
                        gain += (target - gain) * 0.002f
                        buf[i] *= gain
                    }
                    track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
                    if (since != 0L) {
                        stats.add((System.nanoTime() - since) / 1e6 + bufferMs)
                        pendingSinceNs = 0L
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "loop thread: ${e.message}")
            } finally {
                runCatching { track.stop() }
                track.release()
            }
        }, "DownshiftLoop").apply { start() }
    }

    /** Same loop, new audio usage (the focus experiments compare usages). */
    fun restart(usage: Int) {
        val loop = lastLoop ?: return
        start(loop, lastRate, usage)
    }

    fun setThrottle(t: Double) {
        throttle = t.coerceIn(0.0, 1.0)
        pendingSinceNs = System.nanoTime()
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }
}
