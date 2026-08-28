package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EgrTemperatureMonitor {
    private val _state = MutableStateFlow(EgrTemperature.State())
    val state: StateFlow<EgrTemperature.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val c = if (prefs.egrTempSimC > 0f) prefs.egrTempSimC else signals.egrTempC
        val st = EgrTemperature.evaluate(c, signals.speedKmh, prefs.egrTempWarnC, prefs.egrTempAlertC, prefs.egrTempSpeedMinKmh)
        if (!prefs.egrTempEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.tempC ?: 0f).toInt() / 25}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.egrTempTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = EgrTemperature.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "egr_temp_alert" else "egr_temp_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "egr_temp:${st.band}:${nowMs / 60000}", false)
        }
    }
}
