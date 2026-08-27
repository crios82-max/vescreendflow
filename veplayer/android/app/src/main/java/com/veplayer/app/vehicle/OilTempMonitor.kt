package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Engine oil over-temp (OBD 015C) → TTS + inbox.
 */
object OilTempMonitor {
    private val _state = MutableStateFlow(OilTemp.State())
    val state: StateFlow<OilTemp.State> = _state.asStateFlow()

    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 2_000L
    private const val COOLDOWN_MS = 45_000L

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val oil =
            when {
                prefs.oilTempSimC > 0f -> prefs.oilTempSimC
                else -> signals.oilTempC
            }
        val st =
            OilTemp.evaluate(
                oilTempC = oil,
                warnC = prefs.oilTempWarnC,
                alertC = prefs.oilTempAlertC,
            )
        if (!prefs.oilTempEnabled) {
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
        val key = "${st.band}:${st.label}"
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.oilTempTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = OilTemp.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "oil_alert" else "oil_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "oil:${st.band}:${nowMs / 120_000}",
                speak = false,
            )
        }
    }
}
