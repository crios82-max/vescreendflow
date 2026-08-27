package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DriverTorqueMonitor {
    private val _state = MutableStateFlow(DriverTorque.State())
    val state: StateFlow<DriverTorque.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val t = if (prefs.drvTorqueSimPct != 0f) prefs.drvTorqueSimPct else signals.driverTorquePct
        val speed = if (prefs.drvTorqueSimPct != 0f) signals.speedKmh.coerceAtLeast(prefs.drvTorqueSpeedMinKmh + 1f) else signals.speedKmh
        val st = DriverTorque.evaluate(t, speed, prefs.drvTorqueWarnPct, prefs.drvTorqueAlertPct, prefs.drvTorqueSpeedMinKmh)
        if (!prefs.drvTorqueEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.torquePct ?: 0f).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.drvTorqueTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = DriverTorque.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "drv_torque_alert" else "drv_torque_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "drv_torque:${st.band}:${nowMs / 60000}", false)
        }
    }
}
