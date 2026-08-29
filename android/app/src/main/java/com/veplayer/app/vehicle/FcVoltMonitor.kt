package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FcVoltMonitor {
    private val _state = MutableStateFlow(FcVolt.State())
    val state: StateFlow<FcVolt.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = if (prefs.fcVoltSimV > 0f) prefs.fcVoltSimV else signals.fcVoltV
        val st = FcVolt.evaluate(p, prefs.fcVoltWarnV, prefs.fcVoltAlertV)
        if (!prefs.fcVoltEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.volts?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.fcVoltTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = FcVolt.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "fc_volt_alert" else "fc_volt_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "fc_volt:${st.band}:${nowMs / 60000}", false)
        }
    }
}
