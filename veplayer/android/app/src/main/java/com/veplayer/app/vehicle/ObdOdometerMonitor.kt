package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ObdOdometerMonitor {
    private val _state = MutableStateFlow(ObdOdometer.State())
    val state: StateFlow<ObdOdometer.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val km = when {
            prefs.obdOdoSimKm > 0f -> prefs.obdOdoSimKm
            else -> signals.obdOdometerKm
        }
        val speed = when {
            prefs.obdOdoSimKm > 0f && prefs.obdOdoSimSpeedKmh > 0f -> prefs.obdOdoSimSpeedKmh
            prefs.obdOdoSimKm > 0f -> signals.speedKmh.coerceAtLeast(prefs.obdOdoSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = ObdOdometer.evaluate(km, speed, prefs.obdOdoWarnKm, prefs.obdOdoAlertKm, prefs.obdOdoSpeedMinKmh)
        if (!prefs.obdOdoEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${((st.odometerKm ?: 0f) / 10000).toInt()}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.obdOdoTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = ObdOdometer.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "obd_odometer_alert" else "obd_odometer_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "obd_odometer:${st.band}:${nowMs / 60000}", false)
        }
    }
}
