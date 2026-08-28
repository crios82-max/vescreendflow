package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EvapSysVaporMonitor {
    private val _state = MutableStateFlow(EvapSysVapor.State())
    val state: StateFlow<EvapSysVapor.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val pa = when {
            prefs.evapSysVaporSimPa != 0f -> prefs.evapSysVaporSimPa
            else -> signals.evapSysVaporPa
        }
        val speed = when {
            prefs.evapSysVaporSimPa != 0f && prefs.evapSysVaporSimSpeedKmh > 0f -> prefs.evapSysVaporSimSpeedKmh
            prefs.evapSysVaporSimPa != 0f -> signals.speedKmh.coerceAtLeast(prefs.evapSysVaporSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = EvapSysVapor.evaluate(pa, speed, prefs.evapSysVaporWarnPa, prefs.evapSysVaporAlertPa, prefs.evapSysVaporSpeedMinKmh)
        if (!prefs.evapSysVaporEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${((st.pressurePa ?: 0f) / 500).toInt()}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.evapSysVaporTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = EvapSysVapor.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "evap_sys_vapor_alert" else "evap_sys_vapor_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "evap_sys_vapor:${st.band}:${nowMs / 60000}", false)
        }
    }
}
