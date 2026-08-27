package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FuelRailAbsMonitor {
    private val _state = MutableStateFlow(FuelRailAbs.State())
    val state: StateFlow<FuelRailAbs.State> = _state.asStateFlow()
    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 2_000L
    private const val COOLDOWN_MS = 30_000L

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val kpa =
            when {
                prefs.railAbsSimKpa > 0f -> prefs.railAbsSimKpa
                else -> signals.fuelRailAbsKpa
            }
        val speed =
            when {
                prefs.railAbsSimKpa > 0f && prefs.railAbsSimSpeedKmh > 0f -> prefs.railAbsSimSpeedKmh
                prefs.railAbsSimKpa > 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.railAbsSpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            FuelRailAbs.evaluate(
                pressureKpa = kpa,
                speedKmh = speed,
                warnKpa = prefs.railAbsWarnKpa,
                alertKpa = prefs.railAbsAlertKpa,
                speedMinKmh = prefs.railAbsSpeedMinKmh,
            )
        if (!prefs.railAbsEnabled) {
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
        val key = "${st.band}:${(st.pressureKpa ?: 0f).toInt() / 500}"
        if (held && (cooled || key != lastKey) && prefs.railAbsTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = FuelRailAbs.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "rail_abs_alert" else "rail_abs_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "rail_abs:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
