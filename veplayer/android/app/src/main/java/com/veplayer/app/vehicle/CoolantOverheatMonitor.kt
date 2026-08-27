package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coolant overheat → TTS + inbox.
 */
object CoolantOverheatMonitor {
    private val _state = MutableStateFlow(CoolantOverheat.State())
    val state: StateFlow<CoolantOverheat.State> = _state.asStateFlow()

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
        val coolant =
            when {
                prefs.coolantSimC > 0f -> prefs.coolantSimC
                else -> signals.coolantC
            }
        val st =
            CoolantOverheat.evaluate(
                coolantC = coolant,
                warnC = prefs.coolantWarnC,
                alertC = prefs.coolantAlertC,
            )
        if (!prefs.coolantEnabled) {
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
        if (held && (cooled || changed) && prefs.coolantTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = CoolantOverheat.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "coolant_overheat" else "coolant_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "coolant:${st.band}:${nowMs / 120_000}",
                speak = false,
            )
        }
    }
}
