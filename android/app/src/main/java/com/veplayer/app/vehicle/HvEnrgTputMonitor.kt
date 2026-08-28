package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HvEnrgTputMonitor {
    private val _state = MutableStateFlow(HvEnrgTput.State())
    val state: StateFlow<HvEnrgTput.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val w = if (prefs.hvEnrgTputSimWh > 0f) prefs.hvEnrgTputSimWh else signals.hvEnrgTputWh
        val st = HvEnrgTput.evaluate(w, prefs.hvEnrgTputWarnWh, prefs.hvEnrgTputAlertWh)
        if (!prefs.hvEnrgTputEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.wh?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.hvEnrgTputTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = HvEnrgTput.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "hv_enrg_tput_alert" else "hv_enrg_tput_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "hv_enrg_tput:${st.band}:${nowMs / 60000}", false)
        }
    }
}
