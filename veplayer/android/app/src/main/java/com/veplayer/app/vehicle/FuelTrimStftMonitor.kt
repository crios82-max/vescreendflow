package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High short-term fuel trim while moving (OBD 0106) → TTS + inbox.
 */
object FuelTrimStftMonitor {
    private val _state = MutableStateFlow(FuelTrimStft.State())
    val state: StateFlow<FuelTrimStft.State> = _state.asStateFlow()

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
                prefs.stftSimPct != 0f -> prefs.stftSimPct
                else -> signals.fuelTrimStftPct
            }
        val speed =
            when {
                prefs.stftSimPct != 0f && prefs.stftSimSpeedKmh > 0f -> prefs.stftSimSpeedKmh
                prefs.stftSimPct != 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.stftSpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            FuelTrimStft.evaluate(
                trimPct = trim,
                speedKmh = speed,
                warnPct = prefs.stftWarnPct,
                alertPct = prefs.stftAlertPct,
                speedMinKmh = prefs.stftSpeedMinKmh,
            )
        if (!prefs.stftEnabled) {
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
        if (held && (cooled || changed) && prefs.stftTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = FuelTrimStft.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "stft_alert" else "stft_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "stft:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
