package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FcFuelRateMonitor {
    private val _state = MutableStateFlow(FcFuelRate.State())
    val state: StateFlow<FcFuelRate.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = if (prefs.fcFuelRateSimGps > 0f) prefs.fcFuelRateSimGps else signals.fcFuelRateGps
        val st = FcFuelRate.evaluate(p, prefs.fcFuelRateWarnGps, prefs.fcFuelRateAlertGps)
        if (!prefs.fcFuelRateEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${((st.gps ?: 0f) * 10).toInt()}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.fcFuelRateTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = FcFuelRate.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "fc_fuel_rate_alert" else "fc_fuel_rate_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "fc_fuel_rate:${st.band}:${nowMs / 60000}", false)
        }
    }
}
