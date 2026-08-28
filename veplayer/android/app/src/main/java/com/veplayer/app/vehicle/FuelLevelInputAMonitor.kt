package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FuelLevelInputAMonitor {
    private val _state = MutableStateFlow(FuelLevelInputA.State())
    val state: StateFlow<FuelLevelInputA.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val pct = when {
            prefs.fuelLvlASimPct > 0f -> prefs.fuelLvlASimPct
            else -> signals.fuelLevelInputAPct
        }
        val speed = when {
            prefs.fuelLvlASimPct > 0f && prefs.fuelLvlASimSpeedKmh > 0f -> prefs.fuelLvlASimSpeedKmh
            prefs.fuelLvlASimPct > 0f -> signals.speedKmh.coerceAtLeast(prefs.fuelLvlASpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = FuelLevelInputA.evaluate(pct, speed, prefs.fuelLvlAWarnPct, prefs.fuelLvlAAlertPct, prefs.fuelLvlASpeedMinKmh)
        if (!prefs.fuelLvlAEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.levelPct?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.fuelLvlATts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = FuelLevelInputA.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "fuel_level_a_alert" else "fuel_level_a_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "fuel_level_a:${st.band}:${nowMs / 60000}", false)
        }
    }
}
