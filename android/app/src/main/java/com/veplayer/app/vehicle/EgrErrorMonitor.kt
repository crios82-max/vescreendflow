package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EgrErrorMonitor {
    private val _state = MutableStateFlow(EgrError.State())
    val state: StateFlow<EgrError.State> = _state.asStateFlow()

    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 2_000L
    private const val COOLDOWN_MS = 30_000L

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val err =
            when {
                prefs.egrSimPct != 0f -> prefs.egrSimPct
                else -> signals.egrErrorPct
            }
        val speed =
            when {
                prefs.egrSimPct != 0f && prefs.egrSimSpeedKmh > 0f -> prefs.egrSimSpeedKmh
                prefs.egrSimPct != 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.egrSpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            EgrError.evaluate(
                errorPct = err,
                speedKmh = speed,
                warnPct = prefs.egrWarnPct,
                alertPct = prefs.egrAlertPct,
                speedMinKmh = prefs.egrSpeedMinKmh,
            )
        if (!prefs.egrEnabled) {
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
        val key = "${st.band}:${(st.errorPct ?: 0f).toInt() / 3}"
        if (held && (cooled || key != lastKey) && prefs.egrTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = EgrError.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "egr_error_alert" else "egr_error_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "egr:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
