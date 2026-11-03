package com.downshift.app.adapters.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.util.Log
import com.downshift.app.core.situation.PriorityAudio

/**
 * Notices sound that has to come through the engine: navigation guidance and the voice assistant, calls and ringing,
 * alarms. The engine never asks for audio focus, so it turns itself down instead; M0 showed guidance appears in the
 * playback callback while it plays. Vehicle chimes are system usages, which apps may only see as unknown, so they are
 * not handled here (a real-car check).
 */
class PlaybackWatcher(context: Context) {
    private val tag = "DownshiftAudio"
    private val audio = context.getSystemService(AudioManager::class.java)
    private var started = false

    @Volatile var priority = PriorityAudio.NONE
        private set

    private val callback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) = update(configs)
    }

    fun start() {
        if (started) return
        started = true
        runCatching {
            audio.registerAudioPlaybackCallback(callback, null)
            update(audio.activePlaybackConfigurations)
        }.onFailure { Log.w(tag, "playback callback: ${it.message}") }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { audio.unregisterAudioPlaybackCallback(callback) }
        priority = PriorityAudio.NONE
    }

    private fun update(configs: List<AudioPlaybackConfiguration>) {
        val p = configs.map { of(it.audioAttributes.usage) }.maxByOrNull { it.ordinal } ?: PriorityAudio.NONE
        if (p != priority) Log.i(tag, "priority $p")
        priority = p
    }

    private fun of(usage: Int) = when (usage) {
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, AudioAttributes.USAGE_ASSISTANT -> PriorityAudio.GUIDANCE
        AudioAttributes.USAGE_VOICE_COMMUNICATION, AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING,
        AudioAttributes.USAGE_NOTIFICATION_RINGTONE -> PriorityAudio.CALL
        AudioAttributes.USAGE_ALARM -> PriorityAudio.ALERT
        else -> PriorityAudio.NONE // the engine's own GAME track, music, clicks
    }
}
