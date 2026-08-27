package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EngineRefTorqueMonitor {
    private val _state = MutableStateFlow(EngineRefTorque.State())
    val state: StateFlow<EngineRefTorque.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val nm = if (prefs.refTorqueSimNm > 0f) prefs.refTorqueSimNm else signals.engineRefTorqueNm
        val st = EngineRefTorque.evaluate(nm, prefs.refTorqueWarnLowNm, prefs.refTorqueAlertLowNm, prefs.refTorqueWarnHighNm, prefs.refTorqueAlertHighNm)
        if (!prefs.refTorqueEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.torqueNm ?: 0f).toInt() / 50}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.refTorqueTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = EngineRefTorque.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "ref_torque_alert" else "ref_torque_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "ref_torque:${st.band}:${nowMs / 60000}", false)
        }
    }
}
