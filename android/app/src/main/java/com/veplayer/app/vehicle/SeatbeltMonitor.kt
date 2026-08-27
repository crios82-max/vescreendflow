package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Seatbelt unlatched while moving → TTS + inbox.
 */
object SeatbeltMonitor {
    private val _state = MutableStateFlow(Seatbelt.State())
    val state: StateFlow<Seatbelt.State> = _state.asStateFlow()

    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 1_200L
    private const val COOLDOWN_MS = 25_000L

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val st =
            Seatbelt.evaluate(
                signals = signals,
                warnKmh = prefs.seatbeltWarnKmh,
                alertKmh = prefs.seatbeltAlertKmh,
            )
        if (!prefs.seatbeltEnabled) {
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
        val key = st.band
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.seatbeltTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = Seatbelt.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "seatbelt_alert" else "seatbelt_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "seatbelt:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
