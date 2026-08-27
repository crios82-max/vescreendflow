package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AbsoluteLoadMonitor {
    private val _state = MutableStateFlow(AbsoluteLoad.State())
    val state: StateFlow<AbsoluteLoad.State> = _state.asStateFlow()

    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 2_000L
    private const val COOLDOWN_MS = 30_000L

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val load =
            when {
                prefs.absLoadSimPct > 0f -> prefs.absLoadSimPct
                else -> signals.absoluteLoadPct
            }
        val speed =
            when {
                prefs.absLoadSimPct > 0f && prefs.absLoadSimSpeedKmh > 0f -> prefs.absLoadSimSpeedKmh
                prefs.absLoadSimPct > 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.absLoadSpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            AbsoluteLoad.evaluate(
                loadPct = load,
                speedKmh = speed,
                warnPct = prefs.absLoadWarnPct,
                alertPct = prefs.absLoadAlertPct,
                speedMinKmh = prefs.absLoadSpeedMinKmh,
            )
        if (!prefs.absLoadEnabled) {
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
        val key = "${st.band}:${(st.loadPct ?: 0f).toInt() / 5}"
        if (held && (cooled || key != lastKey) && prefs.absLoadTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = AbsoluteLoad.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "abs_load_alert" else "abs_load_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "abs_load:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
