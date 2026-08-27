package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High engine load while moving → TTS + inbox.
 */
object EngineLoadMonitor {
    private val _state = MutableStateFlow(EngineLoad.State())
    val state: StateFlow<EngineLoad.State> = _state.asStateFlow()

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
        val load =
            when {
                prefs.engineLoadSimPct > 0f -> prefs.engineLoadSimPct
                else -> signals.engineLoadPct
            }
        val speed =
            when {
                prefs.engineLoadSimPct > 0f && prefs.engineLoadSimSpeedKmh > 0f ->
                    prefs.engineLoadSimSpeedKmh
                prefs.engineLoadSimPct > 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.engineLoadSpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            EngineLoad.evaluate(
                loadPct = load,
                speedKmh = speed,
                warnPct = prefs.engineLoadWarnPct,
                alertPct = prefs.engineLoadAlertPct,
                speedMinKmh = prefs.engineLoadSpeedMinKmh,
            )
        if (!prefs.engineLoadEnabled) {
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
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.engineLoadTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = EngineLoad.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "load_alert" else "load_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "load:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
