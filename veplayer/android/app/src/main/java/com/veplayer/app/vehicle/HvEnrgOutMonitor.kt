package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HvEnrgOutMonitor {
    private val _state = MutableStateFlow(HvEnrgOut.State())
    val state: StateFlow<HvEnrgOut.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val k = if (prefs.hvEnrgOutSimKwh > 0f) prefs.hvEnrgOutSimKwh else signals.hvEnrgOutKwh
        val st = HvEnrgOut.evaluate(k, prefs.hvEnrgOutWarnKwh, prefs.hvEnrgOutAlertKwh)
        if (!prefs.hvEnrgOutEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.kwh?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.hvEnrgOutTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = HvEnrgOut.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "hv_enrg_out_alert" else "hv_enrg_out_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "hv_enrg_out:${st.band}:${nowMs / 60000}", false)
        }
    }
}
