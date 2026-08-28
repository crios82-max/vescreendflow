package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HvessTempMonitor {
    private val _state = MutableStateFlow(HvessTemp.State())
    val state: StateFlow<HvessTemp.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val t = if (prefs.hvessTempSimC > 0f) prefs.hvessTempSimC else signals.hvessTempC
        val st = HvessTemp.evaluate(t, prefs.hvessTempWarnC, prefs.hvessTempAlertC)
        if (!prefs.hvessTempEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.tempC?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.hvessTempTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = HvessTemp.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "hvess_temp_alert" else "hvess_temp_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "hvess_temp:${st.band}:${nowMs / 60000}", false)
        }
    }
}
