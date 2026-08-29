package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HvMaxSocMonitor {
    private val _state = MutableStateFlow(HvMaxSoc.State())
    val state: StateFlow<HvMaxSoc.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = if (prefs.hvMaxSocSimPct > 0f) prefs.hvMaxSocSimPct else signals.hvMaxSocPct
        val st = HvMaxSoc.evaluate(p, prefs.hvMaxSocWarnPct, prefs.hvMaxSocAlertPct)
        if (!prefs.hvMaxSocEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.socPct?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.hvMaxSocTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = HvMaxSoc.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "hv_max_soc_alert" else "hv_max_soc_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "hv_max_soc:${st.band}:${nowMs / 60000}", false)
        }
    }
}
