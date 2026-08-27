package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ActualTorqueMonitor {
    private val _state = MutableStateFlow(ActualTorque.State())
    val state: StateFlow<ActualTorque.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val t = if (prefs.actTorqueSimPct != 0f) prefs.actTorqueSimPct else signals.actualTorquePct
        val speed = if (prefs.actTorqueSimPct != 0f) signals.speedKmh.coerceAtLeast(prefs.actTorqueSpeedMinKmh + 1f) else signals.speedKmh
        val st = ActualTorque.evaluate(t, speed, prefs.actTorqueWarnPct, prefs.actTorqueAlertPct, prefs.actTorqueSpeedMinKmh)
        if (!prefs.actTorqueEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.torquePct ?: 0f).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.actTorqueTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = ActualTorque.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "act_torque_alert" else "act_torque_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "act_torque:${st.band}:${nowMs / 60000}", false)
        }
    }
}
