package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EthanolPctMonitor {
    private val _state = MutableStateFlow(EthanolPct.State())
    val state: StateFlow<EthanolPct.State> = _state.asStateFlow()
    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 2_000L
    private const val COOLDOWN_MS = 30_000L

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val pct =
            when {
                prefs.ethanolSimPct > 0f -> prefs.ethanolSimPct
                else -> signals.ethanolPct
            }
        val speed =
            when {
                prefs.ethanolSimPct > 0f && prefs.ethanolSimSpeedKmh > 0f -> prefs.ethanolSimSpeedKmh
                prefs.ethanolSimPct > 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.ethanolSpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            EthanolPct.evaluate(
                ethanolPct = pct,
                speedKmh = speed,
                warnPct = prefs.ethanolWarnPct,
                alertPct = prefs.ethanolAlertPct,
                speedMinKmh = prefs.ethanolSpeedMinKmh,
            )
        if (!prefs.ethanolEnabled) {
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
        val key = "${st.band}:${(st.ethanolPct ?: 0f).toInt() / 5}"
        if (held && (cooled || key != lastKey) && prefs.ethanolTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = EthanolPct.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "ethanol_alert" else "ethanol_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "ethanol:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
