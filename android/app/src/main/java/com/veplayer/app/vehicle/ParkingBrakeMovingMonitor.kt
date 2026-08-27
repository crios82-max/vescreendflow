package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Parking brake while moving → TTS + inbox.
 */
object ParkingBrakeMovingMonitor {
    private val _state = MutableStateFlow(ParkingBrakeMoving.State())
    val state: StateFlow<ParkingBrakeMoving.State> = _state.asStateFlow()

    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 1_500L
    private const val COOLDOWN_MS = 25_000L

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val brake =
            when {
                prefs.pbrakeSim -> true
                else -> signals.parkingBrake
            }
        val speed =
            when {
                prefs.pbrakeSim && prefs.pbrakeSimKmh > 0f -> prefs.pbrakeSimKmh
                prefs.pbrakeSim -> signals.speedKmh.coerceAtLeast(prefs.pbrakeWarnKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            ParkingBrakeMoving.evaluate(
                parkingBrake = brake,
                speedKmh = speed,
                warnKmh = prefs.pbrakeWarnKmh,
                alertKmh = prefs.pbrakeAlertKmh,
            )
        if (!prefs.pbrakeEnabled) {
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
        val key = st.band
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.pbrakeTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = ParkingBrakeMoving.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "pbrake_alert" else "pbrake_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "pbrake:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
