package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HvDcapMonitor {
    private val _state = MutableStateFlow(HvDcap.State())
    val state: StateFlow<HvDcap.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = if (prefs.hvDcapSimKwh > 0f) prefs.hvDcapSimKwh else signals.hvDcapKwh
        val st = HvDcap.evaluate(p, prefs.hvDcapWarnKwh, prefs.hvDcapAlertKwh)
        if (!prefs.hvDcapEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.kwh?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.hvDcapTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = HvDcap.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "hv_dcap_alert" else "hv_dcap_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "hv_dcap:${st.band}:${nowMs / 60000}", false)
        }
    }
}
