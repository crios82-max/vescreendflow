package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ActualEgrMonitor {
    private val _state = MutableStateFlow(ActualEgr.State())
    val state: StateFlow<ActualEgr.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = if (prefs.egrActualSimPct > 0f) prefs.egrActualSimPct else signals.actualEgrPct
        val st = ActualEgr.evaluate(p, signals.speedKmh, prefs.egrActualWarnPct, prefs.egrActualAlertPct, prefs.egrActualSpeedMinKmh)
        if (!prefs.egrActualEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.egrPct ?: 0f).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.egrActualTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = ActualEgr.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "egr_actual_alert" else "egr_actual_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "egr_actual:${st.band}:${nowMs / 60000}", false)
        }
    }
}
