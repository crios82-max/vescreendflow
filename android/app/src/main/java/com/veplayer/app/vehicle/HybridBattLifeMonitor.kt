package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HybridBattLifeMonitor {
    private val _state = MutableStateFlow(HybridBattLife.State())
    val state: StateFlow<HybridBattLife.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val pct = if (prefs.hybridSimPct > 0f) prefs.hybridSimPct else signals.hybridBattLifePct
        val speed = signals.speedKmh
        val st = HybridBattLife.evaluate(pct, speed, prefs.hybridWarnPct, prefs.hybridAlertPct, prefs.hybridSpeedMinKmh)
        if (!prefs.hybridEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.lifePct ?: 0f).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.hybridTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = HybridBattLife.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "hybrid_batt_alert" else "hybrid_batt_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "hybrid_batt:${st.band}:${nowMs / 60000}", false)
        }
    }
}
