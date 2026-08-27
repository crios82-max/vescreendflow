package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** High timing advance (OBD 010E) → TTS + inbox. */
object TimingAdvanceMonitor {
    private val _state = MutableStateFlow(TimingAdvance.State())
    val state: StateFlow<TimingAdvance.State> = _state.asStateFlow()

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
        val deg =
            when {
                prefs.timingSimDeg != 0f -> prefs.timingSimDeg
                else -> signals.timingAdvanceDeg
            }
        val speed =
            when {
                prefs.timingSimDeg != 0f && prefs.timingSimSpeedKmh > 0f -> prefs.timingSimSpeedKmh
                prefs.timingSimDeg != 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.timingSpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val rpm =
            when {
                prefs.timingSimDeg != 0f && signals.rpm != null -> signals.rpm
                else -> signals.rpm
            }
        val st =
            TimingAdvance.evaluate(
                timingDeg = deg,
                speedKmh = speed,
                rpm = rpm,
                warnDeg = prefs.timingWarnDeg,
                alertDeg = prefs.timingAlertDeg,
                speedMinKmh = prefs.timingSpeedMinKmh,
                rpmMin = prefs.timingRpmMin,
            )
        if (!prefs.timingEnabled) {
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
        val key = "${st.band}:${(st.timingDeg ?: 0f).toInt() / 3}"
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.timingTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = TimingAdvance.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "timing_alert" else "timing_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "timing:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
