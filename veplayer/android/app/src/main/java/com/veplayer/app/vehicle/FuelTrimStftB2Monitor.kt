package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High short-term fuel trim bank 2 while moving (OBD 0108) → TTS + inbox.
 */
object FuelTrimStftB2Monitor {
    private val _state = MutableStateFlow(FuelTrimStftB2.State())
    val state: StateFlow<FuelTrimStftB2.State> = _state.asStateFlow()

    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 2_000L
    private const val COOLDOWN_MS = 30_000L

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val trim =
            when {
                prefs.stftB2SimPct != 0f -> prefs.stftB2SimPct
                else -> signals.fuelTrimStftB2Pct
            }
        val speed =
            when {
                prefs.stftB2SimPct != 0f && prefs.stftB2SimSpeedKmh > 0f -> prefs.stftB2SimSpeedKmh
                prefs.stftB2SimPct != 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.stftB2SpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            FuelTrimStftB2.evaluate(
                trimPct = trim,
                speedKmh = speed,
                warnPct = prefs.stftB2WarnPct,
                alertPct = prefs.stftB2AlertPct,
                speedMinKmh = prefs.stftB2SpeedMinKmh,
            )
        if (!prefs.stftB2Enabled) {
            _state.value = st.copy(showWarn = false)
            return
        }
        _state.value = st

        if (!st.showWarn) {
            warnSinceMs = 0L
            lastKey = ""
            return
        }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val held = nowMs - warnSinceMs >= HOLD_MS
        val cooled = nowMs - lastSpokenMs >= COOLDOWN_MS
        val key = "${st.band}:${(st.trimPct ?: 0f).toInt() / 3}"
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.stftB2Tts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = FuelTrimStftB2.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "stft_b2_alert" else "stft_b2_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "stft_b2:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
