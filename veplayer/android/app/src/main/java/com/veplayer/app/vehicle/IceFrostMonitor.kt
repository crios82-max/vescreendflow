package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Outdoor ice / frost → TTS + inbox.
 */
object IceFrostMonitor {
    private val _state = MutableStateFlow(IceFrost.State())
    val state: StateFlow<IceFrost.State> = _state.asStateFlow()

    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 2_000L
    private const val COOLDOWN_MS = 60_000L

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val outdoor =
            when {
                prefs.iceSimOn -> prefs.iceSimC
                else -> signals.outdoorTempC
            }
        val st =
            IceFrost.evaluate(
                outdoorC = outdoor,
                warnC = prefs.iceWarnC,
                alertC = prefs.iceAlertC,
            )
        if (!prefs.iceEnabled) {
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
        if (held && (cooled || changed) && prefs.iceTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = IceFrost.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "ice_alert" else "ice_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "ice:${st.band}:${nowMs / 300_000}",
                speak = false,
            )
        }
    }
}
