package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TurboInletPressureMonitor {
    private val _state = MutableStateFlow(TurboInletPressure.State())
    val state: StateFlow<TurboInletPressure.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = if (prefs.turboInletSimKpa > 0f) prefs.turboInletSimKpa else signals.turboInletKpa
        val st = TurboInletPressure.evaluate(p, signals.speedKmh, prefs.turboInletWarnKpa, prefs.turboInletAlertKpa, prefs.turboInletSpeedMinKmh)
        if (!prefs.turboInletEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.pressureKpa ?: 0f).toInt() / 10}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.turboInletTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = TurboInletPressure.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "turbo_inlet_alert" else "turbo_inlet_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "turbo_inlet:${st.band}:${nowMs / 60000}", false)
        }
    }
}
