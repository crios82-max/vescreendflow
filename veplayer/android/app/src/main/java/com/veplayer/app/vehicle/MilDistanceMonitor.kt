package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Distance with MIL on (OBD 0121) → TTS + inbox.
 */
object MilDistanceMonitor {
    private val _state = MutableStateFlow(MilDistance.State())
    val state: StateFlow<MilDistance.State> = _state.asStateFlow()

    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 2_000L
    private const val COOLDOWN_MS = 10 * 60_000L

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val milOn = signals.mil || prefs.milDistSimKm > 0f
        val distKm =
            when {
                prefs.milDistSimKm > 0f -> prefs.milDistSimKm
                else -> signals.milDistanceKm
            }
        val st =
            MilDistance.evaluate(
                distanceKm = distKm,
                milOn = milOn,
                warnKm = prefs.milDistWarnKm,
                alertKm = prefs.milDistAlertKm,
            )
        if (!prefs.milDistEnabled) {
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
        val key = "${st.band}:${(st.distanceKm ?: 0f).toInt() / 10}"
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.milDistTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = MilDistance.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "mil_dist_alert" else "mil_dist_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "mil_dist:${st.band}:${nowMs / 600_000}",
                speak = false,
            )
        }
    }
}
