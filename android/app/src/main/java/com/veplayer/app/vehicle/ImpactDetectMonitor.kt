package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Extreme decel / yaw → impact candidate · TTS + inbox.
 */
object ImpactDetectMonitor {
    private val _state = MutableStateFlow(ImpactDetect.State())
    val state: StateFlow<ImpactDetect.State> = _state.asStateFlow()

    private var lastSpeedKmh = 0f
    private var lastTsMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private var simArmed = false
    private const val COOLDOWN_MS = 60_000L

    /** One-shot: next tick injects an impact-level decel. */
    fun armSim() {
        simArmed = true
    }

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (!prefs.impactEnabled) {
            _state.value = ImpactDetect.State()
            lastTsMs = 0L
            return
        }
        val speed = signals.speedKmh.coerceAtLeast(0f)
        var decelKmhS = 0f
        if (lastTsMs > 0L) {
            val dt = ((nowMs - lastTsMs) / 1000f).coerceIn(0.05f, 2f)
            val accel = (speed - lastSpeedKmh) / dt
            decelKmhS = (-accel).coerceAtLeast(0f)
        }
        lastSpeedKmh = speed
        lastTsMs = nowMs

        var yaw = signals.yawRateDegS ?: 0f
        if (simArmed) {
            simArmed = false
            decelKmhS = prefs.impactDecelAlertKmhS + 5f
            yaw = 0f
        }

        val spike =
            decelKmhS >= prefs.impactDecelWarnKmhS || abs(yaw) >= prefs.impactYawWarnDegS
        val speedEval =
            if (spike) max(speed, prefs.impactSpeedMinKmh) else speed

        val st =
            ImpactDetect.evaluate(
                decelKmhS = decelKmhS,
                yawDegS = abs(yaw),
                speedKmh = speedEval,
                decelWarn = prefs.impactDecelWarnKmhS,
                decelAlert = prefs.impactDecelAlertKmhS,
                yawWarn = prefs.impactYawWarnDegS,
                yawAlert = prefs.impactYawAlertDegS,
                speedMinKmh = prefs.impactSpeedMinKmh,
            )
        _state.value = st

        if (!st.showWarn) {
            lastKey = ""
            return
        }
        val cooled = nowMs - lastSpokenMs >= COOLDOWN_MS
        val changed = st.band != lastKey
        if ((cooled || changed) && prefs.impactTts) {
            lastSpokenMs = nowMs
            lastKey = st.band
            val phrase = ImpactDetect.voicePhrase(st)
            if (phrase.isNotBlank()) {
                NavTts.speakNow(phrase)
                FleetInbox.push(
                    prefs = prefs,
                    kind = if (st.band == "alert") "impact_alert" else "impact_warn",
                    text = phrase,
                    severity = if (st.band == "alert") "critical" else "warn",
                    id = "impact:${st.band}:${nowMs / 120_000}",
                    speak = false,
                )
            }
        }
    }
}
