package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RelativeThrottleMonitor {
    private val _state = MutableStateFlow(RelativeThrottle.State())
    val state: StateFlow<RelativeThrottle.State> = _state.asStateFlow()

    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 2_000L
    private const val COOLDOWN_MS = 30_000L

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val thr =
            when {
                prefs.relThrSimPct > 0f -> prefs.relThrSimPct
                else -> signals.relativeThrottlePct
            }
        val speed =
            when {
                prefs.relThrSimPct > 0f && prefs.relThrSimSpeedKmh > 0f -> prefs.relThrSimSpeedKmh
                prefs.relThrSimPct > 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.relThrSpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            RelativeThrottle.evaluate(
                throttlePct = thr,
                speedKmh = speed,
                warnPct = prefs.relThrWarnPct,
                alertPct = prefs.relThrAlertPct,
                speedMinKmh = prefs.relThrSpeedMinKmh,
            )
        if (!prefs.relThrEnabled) {
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
        if (held && (cooled || key != lastKey) && prefs.relThrTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = RelativeThrottle.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "rel_thr_alert" else "rel_thr_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "rel_thr:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
