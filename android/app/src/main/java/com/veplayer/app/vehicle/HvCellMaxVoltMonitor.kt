package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HvCellMaxVoltMonitor {
    private val _state = MutableStateFlow(HvCellMaxVolt.State())
    val state: StateFlow<HvCellMaxVolt.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val v = if (prefs.hvCellMaxVSimV > 0f) prefs.hvCellMaxVSimV else signals.hvCellMaxVoltageV
        val st = HvCellMaxVolt.evaluate(v, prefs.hvCellMaxVWarnV, prefs.hvCellMaxVAlertV)
        if (!prefs.hvCellMaxVEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.volts ?: 0f}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.hvCellMaxVTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = HvCellMaxVolt.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "hv_cell_max_volt_alert" else "hv_cell_max_volt_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "hv_cell_max_volt:${st.band}:${nowMs / 60000}", false)
        }
    }
}
