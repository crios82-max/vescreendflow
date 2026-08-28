package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThrottleActuatorMonitor {
    private val _state = MutableStateFlow(ThrottleActuator.State())
    val state: StateFlow<ThrottleActuator.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = if (prefs.thrActSimPct > 0f) prefs.thrActSimPct else signals.thrActuatorPct
        val st = ThrottleActuator.evaluate(p, signals.speedKmh, prefs.thrActWarnPct, prefs.thrActAlertPct, prefs.thrActSpeedMinKmh)
        if (!prefs.thrActEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.actuatorPct ?: 0f).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.thrActTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = ThrottleActuator.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "thr_act_alert" else "thr_act_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "thr_act:${st.band}:${nowMs / 60000}", false)
        }
    }
}
