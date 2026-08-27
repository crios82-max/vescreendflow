package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks Δspeed → harsh brake/accel · TTS + inbox.
 */
object HarshDrivingMonitor {
    private val _state = MutableStateFlow(HarshDriving.State())
    val state: StateFlow<HarshDriving.State> = _state.asStateFlow()

    private var lastSpeedKmh = 0f
    private var lastTsMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private var simArmed = false
    private const val COOLDOWN_MS = 20_000L

    /** One-shot sim: next tick injects a harsh brake sample. */
    fun armSim() {
        simArmed = true
    }

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (!prefs.harshEnabled) {
            _state.value = HarshDriving.State()
            lastTsMs = 0L
            return
        }
        val speed = signals.speedKmh.coerceAtLeast(0f)
        var accelKmhS = 0f
        if (lastTsMs > 0L) {
            val dt = ((nowMs - lastTsMs) / 1000f).coerceIn(0.05f, 2f)
            accelKmhS = (speed - lastSpeedKmh) / dt
        }
        lastSpeedKmh = speed
        lastTsMs = nowMs

        if (simArmed) {
            simArmed = false
            // Force a brake spike sample for demo
            accelKmhS = -(prefs.harshBrakeAlertKmhS + 2f)
        }

        val st =
            HarshDriving.evaluate(
                accelKmhS = accelKmhS,
                absActive = signals.absActive,
                brakeWarnKmhS = prefs.harshBrakeWarnKmhS,
                brakeAlertKmhS = prefs.harshBrakeAlertKmhS,
                accelWarnKmhS = prefs.harshAccelWarnKmhS,
                accelAlertKmhS = prefs.harshAccelAlertKmhS,
            )
        _state.value = st

        if (!st.showWarn) {
            lastKey = ""
            return
        }
        val cooled = nowMs - lastSpokenMs >= COOLDOWN_MS
        val changed = st.band != lastKey
        if ((cooled || changed) && prefs.harshTts) {
            lastSpokenMs = nowMs
            lastKey = st.band
            val phrase = HarshDriving.voicePhrase(st)
            if (phrase.isNotBlank()) {
                NavTts.speakNow(phrase)
                FleetInbox.push(
                    prefs = prefs,
                    kind = st.band,
                    text = phrase,
                    severity = if (st.band.endsWith("alert")) "critical" else "warn",
                    id = "harsh:${st.band}:${nowMs / 60_000}",
                    speak = false,
                )
            }
        }
    }
}
