package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HevModeMonitor {
    private val _state = MutableStateFlow(HevMode.State())
    val state: StateFlow<HevMode.State> = _state.asStateFlow()
    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        // sim: -1 = off; 0/1/2 = CSM/CDM/CIM
        val value =
            if (prefs.hevModeSim >= 0) prefs.hevModeSim.toFloat()
            else signals.hevModeCode
        val st = HevMode.evaluate(value)
        if (!prefs.hevModeEnabled) {
            _state.value = st.copy(showWarn = false)
            return
        }
        _state.value = st
        if (!st.showWarn) {
            warnSinceMs = 0L
            lastKey = ""
            return
        }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.mode}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.hevModeTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = HevMode.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs,
                if (st.band == "alert") "hev_mode_alert" else "hev_mode_warn",
                phrase,
                if (st.band == "alert") "critical" else "warn",
                "hev_mode:${st.band}:${nowMs / 60000}",
                false,
            )
        }
    }
}
