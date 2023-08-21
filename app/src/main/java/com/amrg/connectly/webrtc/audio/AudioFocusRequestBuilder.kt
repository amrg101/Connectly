package com.amrg.connectly.webrtc.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener

class AudioFocusRequestBuilder {

    @SuppressLint("NewApi")
    fun build(audioFocusChangeListener: OnAudioFocusChangeListener): AudioFocusRequest {
        val playbackAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .build()
        return AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(playbackAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()
    }
}