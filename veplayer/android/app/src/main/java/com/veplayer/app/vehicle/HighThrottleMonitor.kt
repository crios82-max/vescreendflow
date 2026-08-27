package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High throttle while moving → TTS + inbox.
 */
object HighThrottleMonitor {
    private val _state = MutableStateFlow(HighThrottle.State())
    val state: StateFlow<HighThrottle.State> = _state.asStateFlow()

    private var highSinceMs = 0L
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
        val thr =
            when {
                prefs.throttleSimPct > 0f -> prefs.throttleSimPct
                else -> signals.throttlePct
            }
        val speed =
            when {
                prefs.throttleSimPct > 0f && prefs.throttleSimSpeedKmh > 0f ->
                    prefs.throttleSimSpeedKmh
                prefs.throttleSimPct > 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.throttleSpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val warnPct = prefs.throttleWarnPct
        val above = thr != null && thr >= warnPct && speed >= prefs.throttleSpeedMinKmh
        val highForSec =
            if (above) {
                if (highSinceMs == 0L) highSinceMs = nowMs
                ((nowMs - highSinceMs) / 1000f).coerceAtLeast(0f)
            } else {
                highSinceMs = 0L
                0f
            }
        val st =
            HighThrottle.evaluate(
                throttlePct = thr,
                speedKmh = speed,
                highForSec = highForSec,
                warnPct = warnPct,
                alertPct = prefs.throttleAlertPct,
                alertHoldSec = prefs.throttleAlertHoldSec,
                speedMinKmh = prefs.throttleSpeedMinKmh,
            )
        if (!prefs.throttleEnabled) {
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
        val key = "${st.band}:${(st.throttlePct ?: 0f).toInt() / 5}"
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.throttleTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = HighThrottle.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "throttle_alert" else "throttle_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "throttle:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
