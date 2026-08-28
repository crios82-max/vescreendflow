package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MafSensorIatMonitor {
    private val _state = MutableStateFlow(MafSensorIat.State())
    val state: StateFlow<MafSensorIat.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val c = if (prefs.mafIatSimC > 0f) prefs.mafIatSimC else signals.mafSensorIatC
        val st = MafSensorIat.evaluate(c, signals.speedKmh, prefs.mafIatWarnC, prefs.mafIatAlertC, prefs.mafIatSpeedMinKmh)
        if (!prefs.mafIatEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.tempC ?: 0f).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.mafIatTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = MafSensorIat.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "maf_iat_alert" else "maf_iat_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "maf_iat:${st.band}:${nowMs / 60000}", false)
        }
    }
}
