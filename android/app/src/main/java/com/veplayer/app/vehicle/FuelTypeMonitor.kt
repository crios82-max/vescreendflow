package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FuelTypeMonitor {
    private val _state = MutableStateFlow(FuelType.State())
    val state: StateFlow<FuelType.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val code = when {
            prefs.fuelTypeSimCode > 0 -> prefs.fuelTypeSimCode
            else -> signals.fuelTypeCode
        }
        val st = FuelType.evaluate(code, signals.speedKmh, prefs.fuelTypeExpected, prefs.fuelTypeSpeedMinKmh)
        if (!prefs.fuelTypeEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "alert:${st.typeCode ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 60000 || key != lastKey) && prefs.fuelTypeTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = FuelType.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, "fuel_type_alert", phrase, "critical", "fuel_type:${st.typeCode}:${nowMs / 60000}", false)
        }
    }
}
