package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EngineExhaustFlowMonitor {
    private val _state = MutableStateFlow(EngineExhaustFlow.State())
    val state: StateFlow<EngineExhaustFlow.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val flow = when {
            prefs.exhaustFlowSimKgh > 0f -> prefs.exhaustFlowSimKgh
            else -> signals.engineExhaustFlowKgh
        }
        val speed = when {
            prefs.exhaustFlowSimKgh > 0f && prefs.exhaustFlowSimSpeedKmh > 0f -> prefs.exhaustFlowSimSpeedKmh
            prefs.exhaustFlowSimKgh > 0f -> signals.speedKmh.coerceAtLeast(prefs.exhaustFlowSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = EngineExhaustFlow.evaluate(flow, speed, prefs.exhaustFlowWarnKgh, prefs.exhaustFlowAlertKgh, prefs.exhaustFlowSpeedMinKmh)
        if (!prefs.exhaustFlowEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${((st.flowKgh ?: 0f) / 5).toInt()}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.exhaustFlowTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = EngineExhaustFlow.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "exhaust_flow_alert" else "exhaust_flow_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "exhaust_flow:${st.band}:${nowMs / 60000}", false)
        }
    }
}
