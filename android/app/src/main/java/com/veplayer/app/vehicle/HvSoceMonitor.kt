package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HvSoceMonitor {
    private val _state = MutableStateFlow(HvSoce.State())
    val state: StateFlow<HvSoce.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = if (prefs.hvSoceSimPct > 0f) prefs.hvSoceSimPct else signals.hvSocePct
        val st = HvSoce.evaluate(p, prefs.hvSoceWarnPct, prefs.hvSoceAlertPct)
        if (!prefs.hvSoceEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.socePct?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.hvSoceTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = HvSoce.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "hv_soce_alert" else "hv_soce_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "hv_soce:${st.band}:${nowMs / 60000}", false)
        }
    }
}
