package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High MAP while moving (OBD 010B) → TTS + inbox.
 */
object MapPressureMonitor {
    private val _state = MutableStateFlow(MapPressure.State())
    val state: StateFlow<MapPressure.State> = _state.asStateFlow()

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
        val mapKpa =
            when {
                prefs.mapSimKpa > 0f -> prefs.mapSimKpa
                else -> signals.mapKpa
            }
        val speed =
            when {
                prefs.mapSimKpa > 0f && prefs.mapSimSpeedKmh > 0f -> prefs.mapSimSpeedKmh
                prefs.mapSimKpa > 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.mapSpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            MapPressure.evaluate(
                mapKpa = mapKpa,
                speedKmh = speed,
                warnKpa = prefs.mapWarnKpa,
                alertKpa = prefs.mapAlertKpa,
                speedMinKmh = prefs.mapSpeedMinKmh,
            )
        if (!prefs.mapEnabled) {
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
        val key = "${st.band}:${(st.mapKpa ?: 0f).toInt() / 5}"
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.mapTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = MapPressure.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "map_alert" else "map_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "map:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
