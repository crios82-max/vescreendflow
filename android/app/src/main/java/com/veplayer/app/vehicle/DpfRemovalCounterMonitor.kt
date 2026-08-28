package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DpfRemovalCounterMonitor {
    private val _state = MutableStateFlow(DpfRemovalCounter.State())
    val state: StateFlow<DpfRemovalCounter.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val count = when {
            prefs.dpfRemovalSimCount > 0f -> prefs.dpfRemovalSimCount
            else -> signals.dpfRemovalCounter
        }
        val speed = when {
            prefs.dpfRemovalSimCount > 0f && prefs.dpfRemovalSimSpeedKmh > 0f -> prefs.dpfRemovalSimSpeedKmh
            prefs.dpfRemovalSimCount > 0f -> signals.speedKmh.coerceAtLeast(prefs.dpfRemovalSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = DpfRemovalCounter.evaluate(count, speed, prefs.dpfRemovalWarnCount, prefs.dpfRemovalAlertCount, prefs.dpfRemovalSpeedMinKmh)
        if (!prefs.dpfRemovalEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${((st.count ?: 0f) / 20).toInt()}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.dpfRemovalTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = DpfRemovalCounter.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "dpf_removal_alert" else "dpf_removal_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "dpf_removal:${st.band}:${nowMs / 60000}", false)
        }
    }
}
