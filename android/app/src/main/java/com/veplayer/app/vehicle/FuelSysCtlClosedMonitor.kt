package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FuelSysCtlClosedMonitor {
    private val _state = MutableStateFlow(FuelSysCtlClosed.State())
    val state: StateFlow<FuelSysCtlClosed.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val count = when {
            prefs.fuelSysCtlSimCount > 0f -> prefs.fuelSysCtlSimCount
            else -> signals.fuelSysCtlClosedCount
        }
        val speed = when {
            prefs.fuelSysCtlSimCount > 0f && prefs.fuelSysCtlSimSpeedKmh > 0f -> prefs.fuelSysCtlSimSpeedKmh
            prefs.fuelSysCtlSimCount > 0f -> signals.speedKmh.coerceAtLeast(prefs.fuelSysCtlSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = FuelSysCtlClosed.evaluate(count, speed, prefs.fuelSysCtlWarnMin, prefs.fuelSysCtlAlertMin, prefs.fuelSysCtlSpeedMinKmh)
        if (!prefs.fuelSysCtlEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.closedCount?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.fuelSysCtlTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = FuelSysCtlClosed.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "fuel_sys_ctl_alert" else "fuel_sys_ctl_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "fuel_sys_ctl:${st.band}:${nowMs / 60000}", false)
        }
    }
}
