package com.downshift.app.adapters.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/**
 * Stand-ins for other apps' sound in demos and checks, each played the way that app would: a navigation voice
 * (guidance usage, may-duck focus) and music (media usage, full focus). onFocus gets the focus request's result.
 */
object GuidanceTone {
    private const val TAG = "DownshiftAudio"
    private const val RATE = 48_000

    fun guidance(context: Context, onFocus: (Int) -> Unit = {}) = play(
        context, AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, AudioAttributes.CONTENT_TYPE_SPEECH,
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, 1.5, doubleArrayOf(880.0), 0.5, onFocus,
    )

    fun music(context: Context, sec: Int = 10, onFocus: (Int) -> Unit = {}) = play(
        context, AudioAttributes.USAGE_MEDIA, AudioAttributes.CONTENT_TYPE_MUSIC, AudioManager.AUDIOFOCUS_GAIN,
        sec.toDouble(), doubleArrayOf(440.0, 660.0), 0.15, onFocus,
    )

    private fun play(
        context: Context, usage: Int, content: Int, focusGain: Int, sec: Double, tones: DoubleArray, level: Double,
        onFocus: (Int) -> Unit,
    ) {
        val audio = context.getSystemService(AudioManager::class.java)
        Thread({
            try {
                val pcm = FloatArray((RATE * sec).toInt()) { i -> (level * tones.sumOf { sin(2 * PI * it * i / RATE) } / tones.size).toFloat() }
                val attrs = AudioAttributes.Builder().setUsage(usage).setContentType(content).build()
                val focus = AudioFocusRequest.Builder(focusGain).setAudioAttributes(attrs).build()
                onFocus(audio.requestAudioFocus(focus))
                val track = AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(
                        AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 4)
                    .build()
                track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
                track.play()
                Log.i(TAG, "stand-in playing: usage=$usage ${sec}s")
                Thread.sleep((sec * 1000).toLong() + 200)
                track.stop()
                track.release()
                audio.abandonAudioFocusRequest(focus)
            } catch (e: Exception) {
                Log.w(TAG, "stand-in tone: ${e.message}")
            }
        }, "DownshiftStandIn").start()
    }
}
