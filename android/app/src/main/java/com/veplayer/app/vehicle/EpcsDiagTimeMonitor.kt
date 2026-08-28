package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EpcsDiagTimeMonitor {
    private val _state = MutableStateFlow(EpcsDiagTime.State())
    val state: StateFlow<EpcsDiagTime.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val sec = when {
            prefs.epcsTimeSimSec > 0f -> prefs.epcsTimeSimSec
            else -> signals.epcsDiagTimeSec
        }
        val speed = when {
            prefs.epcsTimeSimSec > 0f && prefs.epcsTimeSimSpeedKmh > 0f -> prefs.epcsTimeSimSpeedKmh
            prefs.epcsTimeSimSec > 0f -> signals.speedKmh.coerceAtLeast(prefs.epcsTimeSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = EpcsDiagTime.evaluate(sec, speed, prefs.epcsTimeWarnSec, prefs.epcsTimeAlertSec, prefs.epcsTimeSpeedMinKmh)
        if (!prefs.epcsTimeEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.timeSec ?: 0f).toInt() / 20}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.epcsTimeTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = EpcsDiagTime.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "epcs_time_alert" else "epcs_time_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "epcs_time:${st.band}:${nowMs / 60000}", false)
        }
    }
}
