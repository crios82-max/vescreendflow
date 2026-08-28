package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FuelPressAMonitor {
    private val _state = MutableStateFlow(FuelPressA.State())
    val state: StateFlow<FuelPressA.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val kpa = when {
            prefs.fuelPressASimKpa > 0f -> prefs.fuelPressASimKpa
            else -> signals.fuelPressAKpa
        }
        val speed = when {
            prefs.fuelPressASimKpa > 0f && prefs.fuelPressASimSpeedKmh > 0f -> prefs.fuelPressASimSpeedKmh
            prefs.fuelPressASimKpa > 0f -> signals.speedKmh.coerceAtLeast(prefs.fuelPressASpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = FuelPressA.evaluate(kpa, speed, prefs.fuelPressAWarnKpa, prefs.fuelPressAAlertKpa, prefs.fuelPressASpeedMinKmh)
        if (!prefs.fuelPressAEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${((st.pressureKpa ?: 0f) / 200).toInt()}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.fuelPressATts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = FuelPressA.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "fuel_press_a_alert" else "fuel_press_a_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "fuel_press_a:${st.band}:${nowMs / 60000}", false)
        }
    }
}
