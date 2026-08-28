package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DpfAftertreatmentMonitor {
    private val _state = MutableStateFlow(DpfAftertreatment.State())
    val state: StateFlow<DpfAftertreatment.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = when {
            prefs.dpfTrigSimPct > 0f -> prefs.dpfTrigSimPct
            else -> signals.dpfTriggerPct
        }
        val speed = when {
            prefs.dpfTrigSimPct > 0f && prefs.dpfTrigSimSpeedKmh > 0f -> prefs.dpfTrigSimSpeedKmh
            prefs.dpfTrigSimPct > 0f -> signals.speedKmh.coerceAtLeast(prefs.dpfTrigSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = DpfAftertreatment.evaluate(p, speed, prefs.dpfTrigWarnPct, prefs.dpfTrigAlertPct, prefs.dpfTrigSpeedMinKmh)
        if (!prefs.dpfTrigEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.triggerPct ?: 0f).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.dpfTrigTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = DpfAftertreatment.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "dpf_trigger_alert" else "dpf_trigger_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "dpf_trigger:${st.band}:${nowMs / 60000}", false)
        }
    }
}
