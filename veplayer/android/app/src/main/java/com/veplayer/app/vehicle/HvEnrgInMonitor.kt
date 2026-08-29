package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HvEnrgInMonitor {
    private val _state = MutableStateFlow(HvEnrgIn.State())
    val state: StateFlow<HvEnrgIn.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val k = if (prefs.hvEnrgInSimKwh > 0f) prefs.hvEnrgInSimKwh else signals.hvEnrgInKwh
        val st = HvEnrgIn.evaluate(k, prefs.hvEnrgInWarnKwh, prefs.hvEnrgInAlertKwh)
        if (!prefs.hvEnrgInEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.kwh?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.hvEnrgInTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = HvEnrgIn.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "hv_enrg_in_alert" else "hv_enrg_in_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "hv_enrg_in:${st.band}:${nowMs / 60000}", false)
        }
    }
}
