package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EvapVaporMonitor {
    private val _state = MutableStateFlow(EvapVapor.State())
    val state: StateFlow<EvapVapor.State> = _state.asStateFlow()
    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 2_000L
    private const val COOLDOWN_MS = 30_000L

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val pa =
            when {
                prefs.evapVaporSimPa != 0f -> prefs.evapVaporSimPa
                else -> signals.evapVaporPa
            }
        val speed =
            when {
                prefs.evapVaporSimPa != 0f && prefs.evapVaporSimSpeedKmh > 0f ->
                    prefs.evapVaporSimSpeedKmh
                prefs.evapVaporSimPa != 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.evapVaporSpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            EvapVapor.evaluate(
                pressurePa = pa,
                speedKmh = speed,
                warnAbsPa = prefs.evapVaporWarnPa,
                alertAbsPa = prefs.evapVaporAlertPa,
                speedMinKmh = prefs.evapVaporSpeedMinKmh,
            )
        if (!prefs.evapVaporEnabled) {
            _state.value = st.copy(showWarn = false)
            return
        }
        _state.value = st
        if (!st.showWarn) {
            warnSinceMs = 0L
            lastKey = ""
            return
        }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val held = nowMs - warnSinceMs >= HOLD_MS
        val cooled = nowMs - lastSpokenMs >= COOLDOWN_MS
        val key = "${st.band}:${(st.pressurePa ?: 0f).toInt() / 500}"
        if (held && (cooled || key != lastKey) && prefs.evapVaporTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = EvapVapor.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "evap_vapor_alert" else "evap_vapor_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "evap_vapor:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
