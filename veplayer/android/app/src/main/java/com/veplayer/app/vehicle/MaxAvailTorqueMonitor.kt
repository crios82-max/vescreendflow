package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MaxAvailTorqueMonitor {
    private val _state = MutableStateFlow(MaxAvailTorque.State())
    val state: StateFlow<MaxAvailTorque.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val t = when {
            prefs.maxAvailTorqueSimPct != 0f -> prefs.maxAvailTorqueSimPct
            else -> signals.maxAvailTorquePct
        }
        val st = MaxAvailTorque.evaluate(t, signals.speedKmh, prefs.maxAvailTorqueWarnLow, prefs.maxAvailTorqueAlertLow, prefs.maxAvailTorqueSpeedMinKmh)
        if (!prefs.maxAvailTorqueEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.torquePct ?: 0f).toInt()}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.maxAvailTorqueTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = MaxAvailTorque.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "max_avail_torque_alert" else "max_avail_torque_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "max_avail_torque:${st.band}:${nowMs / 60000}", false)
        }
    }
}
