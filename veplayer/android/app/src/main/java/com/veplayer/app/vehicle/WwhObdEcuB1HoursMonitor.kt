package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object WwhObdEcuB1HoursMonitor {
    private val _state = MutableStateFlow(WwhObdEcuB1Hours.State())
    val state: StateFlow<WwhObdEcuB1Hours.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val h = if (prefs.wwhEcuB1SimH > 0f) prefs.wwhEcuB1SimH else signals.wwhObdEcuB1Hours
        val st = WwhObdEcuB1Hours.evaluate(h, prefs.wwhEcuB1WarnH, prefs.wwhEcuB1AlertH)
        if (!prefs.wwhEcuB1Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.b1Hours ?: 0f).toInt() / 10}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.wwhEcuB1Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = WwhObdEcuB1Hours.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "wwh_ecu_b1_alert" else "wwh_ecu_b1_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "wwh_ecu_b1:${st.band}:${nowMs / 60000}", false)
        }
    }
}
