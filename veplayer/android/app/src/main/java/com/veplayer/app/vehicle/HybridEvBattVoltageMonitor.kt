package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HybridEvBattVoltageMonitor {
    private val _state = MutableStateFlow(HybridEvBattVoltage.State())
    val state: StateFlow<HybridEvBattVoltage.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val v = if (prefs.hevVoltSimV > 0f) prefs.hevVoltSimV else signals.hybridEvBattVoltageV
        val st = HybridEvBattVoltage.evaluate(v, prefs.hevVoltWarnV, prefs.hevVoltAlertV)
        if (!prefs.hevVoltEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.volts?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.hevVoltTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = HybridEvBattVoltage.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "hybrid_ev_batt_alert" else "hybrid_ev_batt_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "hybrid_ev_batt:${st.band}:${nowMs / 60000}", false)
        }
    }
}
