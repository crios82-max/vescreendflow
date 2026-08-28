package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FuelPressureControlMonitor {
    private val _state = MutableStateFlow(FuelPressureControl.State())
    val state: StateFlow<FuelPressureControl.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = if (prefs.fuelCtrlSimKpa > 0f) prefs.fuelCtrlSimKpa else signals.fuelCtrlKpa
        val st = FuelPressureControl.evaluate(p, signals.speedKmh, prefs.fuelCtrlWarnKpa, prefs.fuelCtrlAlertKpa, prefs.fuelCtrlSpeedMinKmh)
        if (!prefs.fuelCtrlEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.pressureKpa ?: 0f).toInt() / 400}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.fuelCtrlTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = FuelPressureControl.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "fuel_ctrl_alert" else "fuel_ctrl_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "fuel_ctrl:${st.band}:${nowMs / 60000}", false)
        }
    }
}
