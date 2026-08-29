package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HvAcrMonitor {
    private val _state = MutableStateFlow(HvAcr.State())
    val state: StateFlow<HvAcr.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = if (prefs.hvAcrSimKw != 0f) prefs.hvAcrSimKw else signals.hvAcrKw
        val st = HvAcr.evaluate(p, prefs.hvAcrWarnKw, prefs.hvAcrAlertKw)
        if (!prefs.hvAcrEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.kw?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.hvAcrTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = HvAcr.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "hv_acr_alert" else "hv_acr_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "hv_acr:${st.band}:${nowMs / 60000}", false)
        }
    }
}
