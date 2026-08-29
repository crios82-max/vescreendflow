package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HvPwrAvailMonitor {
    private val _state = MutableStateFlow(HvPwrAvail.State())
    val state: StateFlow<HvPwrAvail.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = if (prefs.hvPwrSimPct > 0f) prefs.hvPwrSimPct else signals.hvPwrAvailPct
        val st = HvPwrAvail.evaluate(p, prefs.hvPwrWarnPct, prefs.hvPwrAlertPct)
        if (!prefs.hvPwrEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.pct?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.hvPwrTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = HvPwrAvail.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "hv_pwr_avail_alert" else "hv_pwr_avail_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "hv_pwr_avail:${st.band}:${nowMs / 60000}", false)
        }
    }
}
