package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EpcsDiagCountMonitor {
    private val _state = MutableStateFlow(EpcsDiagCount.State())
    val state: StateFlow<EpcsDiagCount.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val n = when {
            prefs.epcsCountSim > 0f -> prefs.epcsCountSim
            else -> signals.epcsDiagCount
        }
        val speed = when {
            prefs.epcsCountSim > 0f && prefs.epcsCountSimSpeedKmh > 0f -> prefs.epcsCountSimSpeedKmh
            prefs.epcsCountSim > 0f -> signals.speedKmh.coerceAtLeast(prefs.epcsCountSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = EpcsDiagCount.evaluate(n, speed, prefs.epcsCountWarn, prefs.epcsCountAlert, prefs.epcsCountSpeedMinKmh)
        if (!prefs.epcsCountEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.count ?: 0f).toInt() / 10}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.epcsCountTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = EpcsDiagCount.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "epcs_count_alert" else "epcs_count_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "epcs_count:${st.band}:${nowMs / 60000}", false)
        }
    }
}
