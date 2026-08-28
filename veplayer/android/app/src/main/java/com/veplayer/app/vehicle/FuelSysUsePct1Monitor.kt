package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FuelSysUsePct1Monitor {
    private val _state = MutableStateFlow(FuelSysUsePct1.State())
    val state: StateFlow<FuelSysUsePct1.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val pct = when {
            prefs.fuelSysUse1SimPct > 0f -> prefs.fuelSysUse1SimPct
            else -> signals.fuelSysUsePct1
        }
        val speed = when {
            prefs.fuelSysUse1SimPct > 0f && prefs.fuelSysUse1SimSpeedKmh > 0f -> prefs.fuelSysUse1SimSpeedKmh
            prefs.fuelSysUse1SimPct > 0f -> signals.speedKmh.coerceAtLeast(prefs.fuelSysUse1SpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = FuelSysUsePct1.evaluate(pct, speed, prefs.fuelSysUse1WarnPct, prefs.fuelSysUse1AlertPct, prefs.fuelSysUse1SpeedMinKmh)
        if (!prefs.fuelSysUse1Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.usePct?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.fuelSysUse1Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = FuelSysUsePct1.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "fuel_sys_use1_alert" else "fuel_sys_use1_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "fuel_sys_use1:${st.band}:${nowMs / 60000}", false)
        }
    }
}
