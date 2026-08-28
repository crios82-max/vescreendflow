package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FuelInjectTimingMonitor {
    private val _state = MutableStateFlow(FuelInjectTiming.State())
    val state: StateFlow<FuelInjectTiming.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val deg = if (prefs.injectSimDeg != 0f) prefs.injectSimDeg else signals.fuelInjectTimingDeg
        val speed = if (prefs.injectSimDeg != 0f) signals.speedKmh.coerceAtLeast(prefs.injectSpeedMinKmh + 1f) else signals.speedKmh
        val st = FuelInjectTiming.evaluate(deg, speed, prefs.injectWarnDeg, prefs.injectAlertDeg, prefs.injectSpeedMinKmh)
        if (!prefs.injectEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.timingDeg ?: 0f).toInt() / 4}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.injectTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = FuelInjectTiming.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "inject_alert" else "inject_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "inject:${st.band}:${nowMs / 60000}", false)
        }
    }
}
