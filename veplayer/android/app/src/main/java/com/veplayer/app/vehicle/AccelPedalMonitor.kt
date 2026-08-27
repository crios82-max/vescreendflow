package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AccelPedalMonitor {
    private val _state = MutableStateFlow(AccelPedal.State())
    val state: StateFlow<AccelPedal.State> = _state.asStateFlow()

    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 2_000L
    private const val COOLDOWN_MS = 30_000L

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val pedal =
            when {
                prefs.accelPedalSimPct > 0f -> prefs.accelPedalSimPct
                else -> signals.accelPedalPct
            }
        val speed =
            when {
                prefs.accelPedalSimPct > 0f && prefs.accelPedalSimSpeedKmh > 0f ->
                    prefs.accelPedalSimSpeedKmh
                prefs.accelPedalSimPct > 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.accelPedalSpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            AccelPedal.evaluate(
                pedalPct = pedal,
                speedKmh = speed,
                warnPct = prefs.accelPedalWarnPct,
                alertPct = prefs.accelPedalAlertPct,
                speedMinKmh = prefs.accelPedalSpeedMinKmh,
            )
        if (!prefs.accelPedalEnabled) {
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
        val key = "${st.band}:${(st.pedalPct ?: 0f).toInt() / 5}"
        if (held && (cooled || key != lastKey) && prefs.accelPedalTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = AccelPedal.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "accel_pedal_alert" else "accel_pedal_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "accel_pedal:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
