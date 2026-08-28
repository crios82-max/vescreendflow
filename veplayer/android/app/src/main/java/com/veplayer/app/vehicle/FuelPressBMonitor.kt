package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FuelPressBMonitor {
    private val _state = MutableStateFlow(FuelPressB.State())
    val state: StateFlow<FuelPressB.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val kpa = when {
            prefs.fuelPressBSimKpa > 0f -> prefs.fuelPressBSimKpa
            else -> signals.fuelPressBKpa
        }
        val speed = when {
            prefs.fuelPressBSimKpa > 0f && prefs.fuelPressBSimSpeedKmh > 0f -> prefs.fuelPressBSimSpeedKmh
            prefs.fuelPressBSimKpa > 0f -> signals.speedKmh.coerceAtLeast(prefs.fuelPressBSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = FuelPressB.evaluate(kpa, speed, prefs.fuelPressBWarnKpa, prefs.fuelPressBAlertKpa, prefs.fuelPressBSpeedMinKmh)
        if (!prefs.fuelPressBEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${((st.pressureKpa ?: 0f) / 200).toInt()}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.fuelPressBTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = FuelPressB.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "fuel_press_b_alert" else "fuel_press_b_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "fuel_press_b:${st.band}:${nowMs / 60000}", false)
        }
    }
}
