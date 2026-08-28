package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ParticulateMonitorMalfunctionCounterMonitor {
    private val _state = MutableStateFlow(ParticulateMonitorMalfunctionCounter.State())
    val state: StateFlow<ParticulateMonitorMalfunctionCounter.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val count = when {
            prefs.particulateMalfSimCount > 0f -> prefs.particulateMalfSimCount
            else -> signals.particulateMonitorMalfunctionCounter
        }
        val speed = when {
            prefs.particulateMalfSimCount > 0f && prefs.particulateMalfSimSpeedKmh > 0f -> prefs.particulateMalfSimSpeedKmh
            prefs.particulateMalfSimCount > 0f -> signals.speedKmh.coerceAtLeast(prefs.particulateMalfSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = ParticulateMonitorMalfunctionCounter.evaluate(count, speed, prefs.particulateMalfWarnCount, prefs.particulateMalfAlertCount, prefs.particulateMalfSpeedMinKmh)
        if (!prefs.particulateMalfEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${((st.count ?: 0f) / 10).toInt()}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.particulateMalfTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = ParticulateMonitorMalfunctionCounter.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "particulate_malf_alert" else "particulate_malf_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "particulate_malf:${st.band}:${nowMs / 60000}", false)
        }
    }
}
