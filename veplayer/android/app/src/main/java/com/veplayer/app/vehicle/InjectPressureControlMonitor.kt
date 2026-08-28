package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object InjectPressureControlMonitor {
    private val _state = MutableStateFlow(InjectPressureControl.State())
    val state: StateFlow<InjectPressureControl.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = if (prefs.injectCtrlSimKpa > 0f) prefs.injectCtrlSimKpa else signals.injectCtrlKpa
        val st = InjectPressureControl.evaluate(p, signals.speedKmh, prefs.injectCtrlWarnKpa, prefs.injectCtrlAlertKpa, prefs.injectCtrlSpeedMinKmh)
        if (!prefs.injectCtrlEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.pressureKpa ?: 0f).toInt() / 500}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.injectCtrlTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = InjectPressureControl.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "inject_ctrl_alert" else "inject_ctrl_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "inject_ctrl:${st.band}:${nowMs / 60000}", false)
        }
    }
}
