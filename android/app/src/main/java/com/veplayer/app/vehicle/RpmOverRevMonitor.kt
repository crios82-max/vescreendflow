package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High RPM / over-rev → TTS + inbox.
 */
object RpmOverRevMonitor {
    private val _state = MutableStateFlow(RpmOverRev.State())
    val state: StateFlow<RpmOverRev.State> = _state.asStateFlow()

    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 1_500L
    private const val COOLDOWN_MS = 30_000L

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val rpm =
            when {
                prefs.rpmSim > 0f -> prefs.rpmSim
                else -> signals.rpm
            }
        val st =
            RpmOverRev.evaluate(
                rpm = rpm,
                warnRpm = prefs.rpmWarn,
                alertRpm = prefs.rpmAlert,
            )
        if (!prefs.rpmEnabled) {
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
        val key = "${st.band}:${(st.rpm ?: 0f).toInt() / 100}"
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.rpmTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = RpmOverRev.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "rpm_alert" else "rpm_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "rpm:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
