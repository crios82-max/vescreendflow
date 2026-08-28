package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CylinderFuelRateMonitor {
    private val _state = MutableStateFlow(CylinderFuelRate.State())
    val state: StateFlow<CylinderFuelRate.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val mg = when {
            prefs.cylFuelSimMg > 0f -> prefs.cylFuelSimMg
            else -> signals.cylinderFuelRateMg
        }
        val speed = when {
            prefs.cylFuelSimMg > 0f && prefs.cylFuelSimSpeedKmh > 0f -> prefs.cylFuelSimSpeedKmh
            prefs.cylFuelSimMg > 0f -> signals.speedKmh.coerceAtLeast(prefs.cylFuelSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = CylinderFuelRate.evaluate(mg, speed, prefs.cylFuelWarnMg, prefs.cylFuelAlertMg, prefs.cylFuelSpeedMinKmh)
        if (!prefs.cylFuelEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.mgPerStroke ?: 0f).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.cylFuelTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = CylinderFuelRate.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "cyl_fuel_alert" else "cyl_fuel_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "cyl_fuel:${st.band}:${nowMs / 60000}", false)
        }
    }
}
