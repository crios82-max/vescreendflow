package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High intake air temp (OBD 010F) → TTS + inbox.
 */
object IntakeAirMonitor {
    private val _state = MutableStateFlow(IntakeAir.State())
    val state: StateFlow<IntakeAir.State> = _state.asStateFlow()

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
        val iat =
            when {
                prefs.intakeAirSimC > 0f -> prefs.intakeAirSimC
                else -> signals.intakeAirC
            }
        val st =
            IntakeAir.evaluate(
                intakeAirC = iat,
                warnC = prefs.intakeAirWarnC,
                alertC = prefs.intakeAirAlertC,
            )
        if (!prefs.intakeAirEnabled) {
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
        if (held && (cooled || changed) && prefs.intakeAirTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = IntakeAir.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "intake_alert" else "intake_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "intake:${st.band}:${nowMs / 120_000}",
                speak = false,
            )
        }
    }
}
