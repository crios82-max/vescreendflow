package com.veplayer.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single audio focus owner for radio / future nav prompts.
 * Spotify App Remote manages its own focus inside Spotify app.
 */
class VeAudioFocus(context: Context) {
    private val am = context.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null
    private var onLost: (() -> Unit)? = null

    private val _hasFocus = MutableStateFlow(false)
    val hasFocus: StateFlow<Boolean> = _hasFocus.asStateFlow()

    private val listener =
        AudioManager.OnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                -> {
                    _hasFocus.value = false
                    onLost?.invoke()
                }
                AudioManager.AUDIOFOCUS_GAIN -> _hasFocus.value = true
            }
        }

    fun request(onLostFocus: () -> Unit): Boolean {
        onLost = onLostFocus
        val attrs =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        val granted =
            if (Build.VERSION.SDK_INT >= 26) {
                val req =
                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener(listener)
                        .setAcceptsDelayedFocusGain(true)
                        .build()
                focusRequest = req
                am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    listener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN,
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        _hasFocus.value = granted
        return granted
    }

    fun abandon() {
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(listener)
        }
        focusRequest = null
        _hasFocus.value = false
        onLost = null
    }
}
