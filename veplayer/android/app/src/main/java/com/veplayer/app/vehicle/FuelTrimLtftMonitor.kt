package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High long-term fuel trim while moving (OBD 0107) → TTS + inbox.
 */
object FuelTrimLtftMonitor {
    private val _state = MutableStateFlow(FuelTrimLtft.State())
    val state: StateFlow<FuelTrimLtft.State> = _state.asStateFlow()

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
                prefs.ltftSimPct != 0f -> prefs.ltftSimPct
                else -> signals.fuelTrimLtftPct
            }
        val speed =
            when {
                prefs.ltftSimPct != 0f && prefs.ltftSimSpeedKmh > 0f -> prefs.ltftSimSpeedKmh
                prefs.ltftSimPct != 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.ltftSpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            FuelTrimLtft.evaluate(
                trimPct = trim,
                speedKmh = speed,
                warnPct = prefs.ltftWarnPct,
                alertPct = prefs.ltftAlertPct,
                speedMinKmh = prefs.ltftSpeedMinKmh,
            )
        if (!prefs.ltftEnabled) {
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
        if (held && (cooled || changed) && prefs.ltftTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = FuelTrimLtft.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "ltft_alert" else "ltft_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "ltft:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
