package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Barometric out-of-range (OBD 0133) → TTS + inbox. */
object BarometricPressureMonitor {
    private val _state = MutableStateFlow(BarometricPressure.State())
    val state: StateFlow<BarometricPressure.State> = _state.asStateFlow()

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
        val baro =
            when {
                prefs.baroSimKpa > 0f -> prefs.baroSimKpa
                else -> signals.baroKpa
            }
        val speed =
            when {
                prefs.baroSimKpa > 0f && prefs.baroSimSpeedKmh > 0f -> prefs.baroSimSpeedKmh
                prefs.baroSimKpa > 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.baroSpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            BarometricPressure.evaluate(
                baroKpa = baro,
                speedKmh = speed,
                warnLowKpa = prefs.baroWarnLowKpa,
                alertLowKpa = prefs.baroAlertLowKpa,
                warnHighKpa = prefs.baroWarnHighKpa,
                alertHighKpa = prefs.baroAlertHighKpa,
                speedMinKmh = prefs.baroSpeedMinKmh,
            )
        if (!prefs.baroEnabled) {
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
        val key = "${st.band}:${(st.baroKpa ?: 0f).toInt() / 3}"
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.baroTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = BarometricPressure.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "baro_alert" else "baro_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "baro:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
