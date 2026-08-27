package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Distance since DTC clear with active faults (OBD 0131) → TTS + inbox.
 */
object DistSinceClearMonitor {
    private val _state = MutableStateFlow(DistSinceClear.State())
    val state: StateFlow<DistSinceClear.State> = _state.asStateFlow()

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
        val faultActive =
            signals.mil ||
                signals.dtcs.isNotEmpty() ||
                signals.dtcCount > 0 ||
                prefs.distClearSimKm > 0f
        val distKm =
            when {
                prefs.distClearSimKm > 0f -> prefs.distClearSimKm
                else -> signals.distSinceClearKm
            }
        val st =
            DistSinceClear.evaluate(
                distanceKm = distKm,
                faultActive = faultActive,
                warnKm = prefs.distClearWarnKm,
                alertKm = prefs.distClearAlertKm,
            )
        if (!prefs.distClearEnabled) {
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
        val key = "${st.band}:${(st.distanceKm ?: 0f).toInt() / 20}"
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.distClearTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = DistSinceClear.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "dist_clear_alert" else "dist_clear_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "dist_clear:${st.band}:${nowMs / 600_000}",
                speak = false,
            )
        }
    }
}
