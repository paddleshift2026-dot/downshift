package com.downshift.app.adapters.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.util.Log
import com.downshift.app.core.probe.ProbeLedger

enum class FocusMode { NONE, GAME, MEDIA, SONIFICATION }

/** The usage the engine track plays with in each experiment (NONE plays as GAME without asking for focus). */
fun FocusMode.trackUsage(): Int = when (this) {
    FocusMode.MEDIA -> AudioAttributes.USAGE_MEDIA
    FocusMode.SONIFICATION -> AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
    else -> AudioAttributes.USAGE_GAME
}

/** Audio focus experiments: what happens to the engine track when guidance speaks or music plays. */
class FocusProbe(context: Context, private val ledger: ProbeLedger) {
    private val tag = "DownshiftAudio"
    private val appContext = context.applicationContext
    private val audio = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            val usages = configs.map { usageName(it.audioAttributes.usage) }.sorted()
            ledger.record("audio.playbacks", usages.joinToString(",").ifEmpty { "(없음)" }, now())
        }
    }

    fun start() {
        ledger.expect("audio.playbacks", "재생 중인 오디오 용도", "AudioPlaybackCallback")
        ledger.expect("audio.guide", "안내음 포커스", "AudioTrack 내비 안내 용도")
        runCatching { audio.registerAudioPlaybackCallback(playbackCallback, null) }
            .onFailure { Log.w(tag, "playback callback: ${it.message}") }
    }

    fun request(mode: FocusMode) {
        abandon()
        val key = "focus.${mode.name}"
        ledger.expect(key, "포커스 실험 ${mode.name}", "AudioManager")
        if (mode == FocusMode.NONE) {
            ledger.record(key, "요청 안 함", now())
            return
        }
        val gain = if (mode == FocusMode.SONIFICATION) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK else AudioManager.AUDIOFOCUS_GAIN
        val req = AudioFocusRequest.Builder(gain)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(mode.trackUsage()).build())
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { change -> ledger.record(key, "변화 ${focusName(change)}", now()) }
            .build()
        request = req
        ledger.record(key, "요청 ${grantName(audio.requestAudioFocus(req))}", now())
    }

    /** A 1.5 s tone with navigation-guidance usage and may-duck focus, standing in for the navi voice. */
    fun playGuidance() = GuidanceTone.guidance(appContext) { r -> ledger.record("audio.guide", "요청 ${grantName(r)}", now()) }

    fun abandon() {
        request?.let { runCatching { audio.abandonAudioFocusRequest(it) } }
        request = null
    }

    fun stop() {
        abandon()
        runCatching { audio.unregisterAudioPlaybackCallback(playbackCallback) }
    }

    private fun now() = System.currentTimeMillis()

    private fun grantName(r: Int) = when (r) {
        AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "허용"
        AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "지연"
        else -> "거부"
    }

    private fun focusName(c: Int) = when (c) {
        AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
        AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
        else -> "code $c"
    }

    private fun usageName(u: Int) = when (u) {
        AudioAttributes.USAGE_MEDIA -> "MEDIA"
        AudioAttributes.USAGE_GAME -> "GAME"
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE -> "NAVIGATION"
        AudioAttributes.USAGE_ASSISTANCE_SONIFICATION -> "SONIFICATION"
        AudioAttributes.USAGE_VOICE_COMMUNICATION -> "CALL"
        AudioAttributes.USAGE_ASSISTANT -> "ASSISTANT"
        AudioAttributes.USAGE_NOTIFICATION -> "NOTIFICATION"
        AudioAttributes.USAGE_ALARM -> "ALARM"
        else -> "usage $u"
    }
}
