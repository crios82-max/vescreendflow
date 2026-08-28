package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MilTimeOnMonitor {
    private val _state = MutableStateFlow(MilTimeOn.State())
    val state: StateFlow<MilTimeOn.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val milOn = signals.mil || prefs.milTimeSimMin > 0
        val min = when {
            prefs.milTimeSimMin > 0 -> prefs.milTimeSimMin
            else -> signals.milTimeMin
        }
        val st = MilTimeOn.evaluate(min, milOn, prefs.milTimeWarnMin, prefs.milTimeAlertMin)
        if (!prefs.milTimeEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.minutes ?: 0) / 10}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 600000 || key != lastKey) && prefs.milTimeTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = MilTimeOn.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "mil_time_alert" else "mil_time_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "mil_time:${st.band}:${nowMs / 600000}", false)
        }
    }
}
