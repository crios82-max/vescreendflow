package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cabin overtemp → TTS + inbox.
 */
object CabinOvertempMonitor {
    private val _state = MutableStateFlow(CabinOvertemp.State())
    val state: StateFlow<CabinOvertemp.State> = _state.asStateFlow()

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
        val cabin =
            when {
                prefs.cabinOvertempSimC > 0f -> prefs.cabinOvertempSimC
                else -> signals.hvacCabinC
            }
        val st =
            CabinOvertemp.evaluate(
                cabinC = cabin,
                outdoorC = signals.outdoorTempC,
                warnC = prefs.cabinWarnC,
                alertC = prefs.cabinAlertC,
            )
        if (!prefs.cabinOvertempEnabled) {
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
        if (held && (cooled || changed) && prefs.cabinOvertempTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = CabinOvertemp.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "cabin_overtemp" else "cabin_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "cabin:${st.band}:${nowMs / 120_000}",
                speak = false,
            )
        }
    }
}
