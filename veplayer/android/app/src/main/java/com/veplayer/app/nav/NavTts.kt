package com.veplayer.app.nav

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.veplayer.app.data.VePrefs
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Speaks navigation cues (Spanish) via [TextToSpeech], ducking media briefly.
 */
object NavTts {
    private const val TAG = "NavTts"

    private var app: Context? = null
    private var prefs: VePrefs? = null
    private var tts: TextToSpeech? = null
    private var ready = AtomicBoolean(false)
    private var job: Job? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    private val spoken = linkedSetOf<String>()
    private var lastDestKey: String = ""

    private val _lastPhrase = MutableStateFlow("")
    val lastPhrase: StateFlow<String> = _lastPhrase.asStateFlow()

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    @Synchronized
    fun start(
        context: Context,
        prefs: VePrefs,
        scope: CoroutineScope,
    ) {
        if (app != null) {
            this.prefs = prefs
            _enabled.value = prefs.navTtsEnabled
            return
        }
        val ctx = context.applicationContext
        app = ctx
        this.prefs = prefs
        audioManager = ctx.getSystemService(AudioManager::class.java)
        _enabled.value = prefs.navTtsEnabled

        tts =
            TextToSpeech(ctx) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    Log.w(TAG, "TTS init failed status=$status")
                    ready.set(false)
                    return@TextToSpeech
                }
                val engine = tts ?: return@TextToSpeech
                val locale = pickLocale(engine)
                val r = engine.setLanguage(locale)
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "locale $locale unsupported, trying default")
                    engine.setLanguage(Locale.getDefault())
                }
                engine.setSpeechRate(1.05f)
                engine.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}

                        override fun onDone(utteranceId: String?) {
                            abandonFocus()
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            abandonFocus()
                        }

                        override fun onError(
                            utteranceId: String?,
                            errorCode: Int,
                        ) {
                            abandonFocus()
                        }
                    },
                )
                ready.set(true)
                Log.i(TAG, "TTS ready locale=$locale")
            }

        job?.cancel()
        job =
            scope.launch(Dispatchers.Default) {
                while (isActive) {
                    tick()
                    delay(2_000)
                }
            }
    }

    fun setEnabled(on: Boolean) {
        prefs?.navTtsEnabled = on
        _enabled.value = on
        if (!on) {
            tts?.stop()
            abandonFocus()
        }
    }

    /** Manual / settings test. */
    fun speakNow(text: String) {
        if (text.isBlank()) return
        speakInternal(text.trim())
    }

    fun stop() {
        job?.cancel()
        job = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready.set(false)
        abandonFocus()
        app = null
    }

    private fun tick() {
        val p = prefs ?: return
        if (!p.navTtsEnabled || !p.navEnabled) return
        if (!ready.get()) return
        val route = NavEngine.route.value
        if (route.steps.isEmpty() || route.source == "off" || route.source == "idle") return

        val destKey = "${route.destinationName}:${route.distanceM.toInt()}"
        if (destKey != lastDestKey) {
            lastDestKey = destKey
            spoken.clear()
            NavGuidance.routeIntro(route)?.let { intro ->
                if (intro.key !in spoken) {
                    spoken += intro.key
                    speakInternal(intro.phrase)
                    _lastPhrase.value = intro.phrase
                }
            }
        }

        val ego = LatLng(p.navFromLat, p.navFromLng)
        val idx = NavGuidance.currentStepIndex(route, ego)
        val remain = NavGuidance.remainOnStepM(route, ego, idx)
        val cue = NavGuidance.nextCue(route, idx, remain, spoken, destKey) ?: return
        spoken += NavGuidance.suppressKeysFor(cue, destKey)
        // Cap memory
        while (spoken.size > 80) {
            val first = spoken.firstOrNull() ?: break
            spoken.remove(first)
        }
        speakInternal(cue.phrase)
        _lastPhrase.value = cue.phrase
    }

    private fun speakInternal(text: String) {
        val engine = tts ?: return
        if (!ready.get()) return
        requestDuckFocus()
        val id = UUID.randomUUID().toString()
        val params = Bundle()
        if (Build.VERSION.SDK_INT >= 21) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
        } else {
            @Suppress("DEPRECATION")
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, hashMapOf(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID to id))
        }
        Log.i(TAG, "speak: $text")
    }

    private fun pickLocale(engine: TextToSpeech): Locale {
        val candidates =
            listOf(
                Locale("es", "VE"),
                Locale("es", "ES"),
                Locale("es", "MX"),
                Locale("es"),
            )
        for (loc in candidates) {
            val r = engine.isLanguageAvailable(loc)
            if (r >= TextToSpeech.LANG_AVAILABLE) return loc
        }
        return Locale.getDefault()
    }

    private fun requestDuckFocus() {
        val am = audioManager ?: return
        val attrs =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        if (Build.VERSION.SDK_INT >= 26) {
            val req =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener { }
                    .build()
            focusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            )
        }
    }

    private fun abandonFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
        focusRequest = null
    }
}
