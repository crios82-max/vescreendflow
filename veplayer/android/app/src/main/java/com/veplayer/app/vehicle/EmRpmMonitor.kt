package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EmRpmMonitor {
    private val _state = MutableStateFlow(EmRpm.State())
    val state: StateFlow<EmRpm.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = if (prefs.emRpmSim > 0f) prefs.emRpmSim else signals.emRpmA
        val st = EmRpm.evaluate(p, prefs.emRpmWarn, prefs.emRpmAlert)
        if (!prefs.emRpmEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.rpm?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.emRpmTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = EmRpm.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "em_rpm_alert" else "em_rpm_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "em_rpm:${st.band}:${nowMs / 60000}", false)
        }
    }
}
