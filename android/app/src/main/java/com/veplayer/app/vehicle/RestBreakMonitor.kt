package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Continuous driving → rest-break warn/alert · TTS + inbox.
 * Resets after stopped ≥ restResetMin.
 */
object RestBreakMonitor {
    private val _state = MutableStateFlow(RestBreak.State())
    val state: StateFlow<RestBreak.State> = _state.asStateFlow()

    private var drivingAccumMs = 0L
    private var stoppedAccumMs = 0L
    private var lastTsMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val COOLDOWN_MS = 15 * 60_000L

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val simMin = prefs.restSimDriveMin
        val speedMin = prefs.restSpeedMinKmh
        val moving =
            when {
                simMin > 0f -> true
                else ->
                    IdleAlert.isIgnitionOn(signals.ignition) &&
                        signals.speedKmh >= speedMin
            }
        if (lastTsMs > 0L) {
            val dt = (nowMs - lastTsMs).coerceIn(0L, 5_000L)
            if (moving) {
                drivingAccumMs += dt
                stoppedAccumMs = 0L
            } else if (drivingAccumMs > 0L || stoppedAccumMs > 0L) {
                stoppedAccumMs += dt
                val resetMs = (prefs.restResetMin * 60f * 1000f).toLong().coerceAtLeast(60_000L)
                if (stoppedAccumMs >= resetMs) {
                    drivingAccumMs = 0L
                    stoppedAccumMs = 0L
                }
            }
        }
        lastTsMs = nowMs

        val drivingSec =
            when {
                simMin > 0f -> simMin * 60f
                else -> drivingAccumMs / 1000f
            }
        val stoppedSec = if (simMin > 0f) 0f else stoppedAccumMs / 1000f
        val st =
            RestBreak.evaluate(
                drivingSec = drivingSec,
                stoppedSec = stoppedSec,
                warnSec = prefs.restDriveWarnMin * 60f,
                alertSec = prefs.restDriveAlertMin * 60f,
            )
        if (!prefs.restBreakEnabled) {
            _state.value = st.copy(showWarn = false)
            return
        }
        _state.value = st

        if (!st.showWarn) {
            lastKey = ""
            return
        }
        val cooled = nowMs - lastSpokenMs >= COOLDOWN_MS
        val key = "${st.band}:${(st.drivingSec / 60).toInt()}"
        val changed = key != lastKey
        if ((cooled || changed) && prefs.restBreakTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = RestBreak.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "rest_break" else "rest_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "rest:${st.band}:${nowMs / 600_000}",
                speak = false,
            )
        }
    }
}
