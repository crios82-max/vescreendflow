package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High fuel rate while moving (OBD 015E) → TTS + inbox.
 */
object FuelRateMonitor {
    private val _state = MutableStateFlow(FuelRate.State())
    val state: StateFlow<FuelRate.State> = _state.asStateFlow()

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
        val gps =
            when {
                prefs.fuelRateSimLph > 0f -> FuelRate.lphToGps(prefs.fuelRateSimLph)
                else -> signals.fuelRateGps
            }
        val speed =
            when {
                prefs.fuelRateSimLph > 0f && prefs.fuelRateSimSpeedKmh > 0f ->
                    prefs.fuelRateSimSpeedKmh
                prefs.fuelRateSimLph > 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.fuelRateSpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            FuelRate.evaluate(
                fuelRateGps = gps,
                speedKmh = speed,
                warnLph = prefs.fuelRateWarnLph,
                alertLph = prefs.fuelRateAlertLph,
                speedMinKmh = prefs.fuelRateSpeedMinKmh,
            )
        if (!prefs.fuelRateEnabled) {
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
        val key = "${st.band}:${(st.fuelRateLph ?: 0f).toInt() / 5}"
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.fuelRateTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = FuelRate.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "fuel_rate_alert" else "fuel_rate_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "fuel_rate:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
