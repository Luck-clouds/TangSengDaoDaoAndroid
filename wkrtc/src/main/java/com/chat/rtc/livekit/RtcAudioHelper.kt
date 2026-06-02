package com.chat.rtc.livekit

import android.content.Context
import android.media.AudioManager

object RtcAudioHelper {
    private var previousMode: Int? = null
    private var previousSpeakerphoneOn: Boolean? = null
    private var previousMicrophoneMute: Boolean? = null

    @JvmStatic
    fun configureForCall(context: Context, videoCall: Boolean) {
        val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (previousMode == null) {
            previousMode = audioManager.mode
            previousSpeakerphoneOn = audioManager.isSpeakerphoneOn
            previousMicrophoneMute = audioManager.isMicrophoneMute
        }
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isMicrophoneMute = false
        audioManager.isSpeakerphoneOn = videoCall
    }

    @JvmStatic
    fun setSpeakerEnabled(context: Context, enabled: Boolean) {
        val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (previousMode == null) {
            previousMode = audioManager.mode
            previousSpeakerphoneOn = audioManager.isSpeakerphoneOn
            previousMicrophoneMute = audioManager.isMicrophoneMute
        }
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isMicrophoneMute = false
        audioManager.isSpeakerphoneOn = enabled
    }

    @JvmStatic
    fun restore(context: Context) {
        val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        previousSpeakerphoneOn?.let { audioManager.isSpeakerphoneOn = it }
        previousMicrophoneMute?.let { audioManager.isMicrophoneMute = it }
        previousMode?.let { audioManager.mode = it }
        previousMode = null
        previousSpeakerphoneOn = null
        previousMicrophoneMute = null
    }
}
