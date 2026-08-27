package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Catalyst over-temp (OBD 0134) → TTS + inbox.
 */
object CatalystTempMonitor {
    private val _state = MutableStateFlow(CatalystTemp.State())
    val state: StateFlow<CatalystTemp.State> = _state.asStateFlow()

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
        val temp =
            when {
                prefs.catalystSimC > 0f -> prefs.catalystSimC
                else -> signals.catalystTempC
            }
        val st =
            CatalystTemp.evaluate(
                catalystTempC = temp,
                warnC = prefs.catalystWarnC,
                alertC = prefs.catalystAlertC,
            )
        if (!prefs.catalystEnabled) {
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
        if (held && (cooled || changed) && prefs.catalystTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = CatalystTemp.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "catalyst_alert" else "catalyst_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "catalyst:${st.band}:${nowMs / 120_000}",
                speak = false,
            )
        }
    }
}
