package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FuelSysUsePct3Monitor {
    private val _state = MutableStateFlow(FuelSysUsePct3.State())
    val state: StateFlow<FuelSysUsePct3.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val pct = when {
            prefs.fuelSysUse3SimPct > 0f -> prefs.fuelSysUse3SimPct
            else -> signals.fuelSysUsePct3
        }
        val speed = when {
            prefs.fuelSysUse3SimPct > 0f && prefs.fuelSysUse3SimSpeedKmh > 0f -> prefs.fuelSysUse3SimSpeedKmh
            prefs.fuelSysUse3SimPct > 0f -> signals.speedKmh.coerceAtLeast(prefs.fuelSysUse3SpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = FuelSysUsePct3.evaluate(pct, speed, prefs.fuelSysUse3WarnPct, prefs.fuelSysUse3AlertPct, prefs.fuelSysUse3SpeedMinKmh)
        if (!prefs.fuelSysUse3Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.usePct?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.fuelSysUse3Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = FuelSysUsePct3.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "fuel_sys_use3_alert" else "fuel_sys_use3_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "fuel_sys_use3:${st.band}:${nowMs / 60000}", false)
        }
    }
}
