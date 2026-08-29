package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HvessPackVoltageMonitor {
    private val _state = MutableStateFlow(HvessPackVoltage.State())
    val state: StateFlow<HvessPackVoltage.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val v = if (prefs.hvessVoltSimV > 0f) prefs.hvessVoltSimV else signals.hvessVoltageV
        val st = HvessPackVoltage.evaluate(v, prefs.hvessVoltWarnV, prefs.hvessVoltAlertV)
        if (!prefs.hvessVoltEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.volts?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.hvessVoltTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = HvessPackVoltage.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "hvess_voltage_alert" else "hvess_voltage_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "hvess_voltage:${st.band}:${nowMs / 60000}", false)
        }
    }
}
