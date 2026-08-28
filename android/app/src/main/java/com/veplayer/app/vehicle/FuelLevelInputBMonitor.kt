package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FuelLevelInputBMonitor {
    private val _state = MutableStateFlow(FuelLevelInputB.State())
    val state: StateFlow<FuelLevelInputB.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val pct = when {
            prefs.fuelLvlBSimPct > 0f -> prefs.fuelLvlBSimPct
            else -> signals.fuelLevelInputBPct
        }
        val speed = when {
            prefs.fuelLvlBSimPct > 0f && prefs.fuelLvlBSimSpeedKmh > 0f -> prefs.fuelLvlBSimSpeedKmh
            prefs.fuelLvlBSimPct > 0f -> signals.speedKmh.coerceAtLeast(prefs.fuelLvlBSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = FuelLevelInputB.evaluate(pct, speed, prefs.fuelLvlBWarnPct, prefs.fuelLvlBAlertPct, prefs.fuelLvlBSpeedMinKmh)
        if (!prefs.fuelLvlBEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.levelPct?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.fuelLvlBTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = FuelLevelInputB.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "fuel_level_b_alert" else "fuel_level_b_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "fuel_level_b:${st.band}:${nowMs / 60000}", false)
        }
    }
}
