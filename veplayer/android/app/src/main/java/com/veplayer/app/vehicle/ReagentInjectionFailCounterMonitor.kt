package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ReagentInjectionFailCounterMonitor {
    private val _state = MutableStateFlow(ReagentInjectionFailCounter.State())
    val state: StateFlow<ReagentInjectionFailCounter.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val count = when {
            prefs.reagentFailSimCount > 0f -> prefs.reagentFailSimCount
            else -> signals.reagentInjectionFailCounter
        }
        val speed = when {
            prefs.reagentFailSimCount > 0f && prefs.reagentFailSimSpeedKmh > 0f -> prefs.reagentFailSimSpeedKmh
            prefs.reagentFailSimCount > 0f -> signals.speedKmh.coerceAtLeast(prefs.reagentFailSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = ReagentInjectionFailCounter.evaluate(count, speed, prefs.reagentFailWarnCount, prefs.reagentFailAlertCount, prefs.reagentFailSpeedMinKmh)
        if (!prefs.reagentFailEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${((st.count ?: 0f) / 10).toInt()}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.reagentFailTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = ReagentInjectionFailCounter.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "reagent_fail_alert" else "reagent_fail_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "reagent_fail:${st.band}:${nowMs / 60000}", false)
        }
    }
}
