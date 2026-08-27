package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Low fuel pressure while moving (OBD 010A) → TTS + inbox.
 */
object FuelPressureMonitor {
    private val _state = MutableStateFlow(FuelPressure.State())
    val state: StateFlow<FuelPressure.State> = _state.asStateFlow()

    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 2_000L
    private const val COOLDOWN_MS = 30_000L

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val kpa =
            when {
                prefs.fuelPressSimKpa > 0f -> prefs.fuelPressSimKpa
                else -> signals.fuelPressureKpa
            }
        val speed =
            when {
                prefs.fuelPressSimKpa > 0f && prefs.fuelPressSimSpeedKmh > 0f ->
                    prefs.fuelPressSimSpeedKmh
                prefs.fuelPressSimKpa > 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.fuelPressSpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            FuelPressure.evaluate(
                pressureKpa = kpa,
                speedKmh = speed,
                warnKpa = prefs.fuelPressWarnKpa,
                alertKpa = prefs.fuelPressAlertKpa,
                speedMinKmh = prefs.fuelPressSpeedMinKmh,
            )
        if (!prefs.fuelPressEnabled) {
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
        val key = "${st.band}:${(st.pressureKpa ?: 0f).toInt() / 10}"
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.fuelPressTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = FuelPressure.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "fuel_press_alert" else "fuel_press_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "fuel_press:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
