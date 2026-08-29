package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EssChgLimMonitor {
    private val _state = MutableStateFlow(EssChgLim.State())
    val state: StateFlow<EssChgLim.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = if (prefs.essChgLimSimKw > 0f) prefs.essChgLimSimKw else signals.essChgLimKw
        val st = EssChgLim.evaluate(p, prefs.essChgLimWarnKw, prefs.essChgLimAlertKw)
        if (!prefs.essChgLimEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.kw?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.essChgLimTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = EssChgLim.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "ess_chg_lim_alert" else "ess_chg_lim_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "ess_chg_lim:${st.band}:${nowMs / 60000}", false)
        }
    }
}
