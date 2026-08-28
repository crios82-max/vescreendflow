package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FuelSysUsePct2Monitor {
    private val _state = MutableStateFlow(FuelSysUsePct2.State())
    val state: StateFlow<FuelSysUsePct2.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val pct = when {
            prefs.fuelSysUse2SimPct > 0f -> prefs.fuelSysUse2SimPct
            else -> signals.fuelSysUsePct2
        }
        val speed = when {
            prefs.fuelSysUse2SimPct > 0f && prefs.fuelSysUse2SimSpeedKmh > 0f -> prefs.fuelSysUse2SimSpeedKmh
            prefs.fuelSysUse2SimPct > 0f -> signals.speedKmh.coerceAtLeast(prefs.fuelSysUse2SpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = FuelSysUsePct2.evaluate(pct, speed, prefs.fuelSysUse2WarnPct, prefs.fuelSysUse2AlertPct, prefs.fuelSysUse2SpeedMinKmh)
        if (!prefs.fuelSysUse2Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.usePct?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.fuelSysUse2Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = FuelSysUsePct2.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "fuel_sys_use2_alert" else "fuel_sys_use2_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "fuel_sys_use2:${st.band}:${nowMs / 60000}", false)
        }
    }
}
