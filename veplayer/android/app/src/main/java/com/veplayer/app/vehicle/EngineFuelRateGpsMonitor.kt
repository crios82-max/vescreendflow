package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EngineFuelRateGpsMonitor {
    private val _state = MutableStateFlow(EngineFuelRateGps.State())
    val state: StateFlow<EngineFuelRateGps.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val rate = when {
            prefs.engineFuelRateGpsSim > 0f -> prefs.engineFuelRateGpsSim
            else -> signals.engineFuelRateGps
        }
        val speed = when {
            prefs.engineFuelRateGpsSim > 0f && prefs.engineFuelRateGpsSimSpeedKmh > 0f -> prefs.engineFuelRateGpsSimSpeedKmh
            prefs.engineFuelRateGpsSim > 0f -> signals.speedKmh.coerceAtLeast(prefs.engineFuelRateGpsSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = EngineFuelRateGps.evaluate(rate, speed, prefs.engineFuelRateGpsWarn, prefs.engineFuelRateGpsAlert, prefs.engineFuelRateGpsSpeedMinKmh)
        if (!prefs.engineFuelRateGpsEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.rateGps?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.engineFuelRateGpsTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = EngineFuelRateGps.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "engine_fuel_rate_gps_alert" else "engine_fuel_rate_gps_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "engine_fuel_rate_gps:${st.band}:${nowMs / 60000}", false)
        }
    }
}
