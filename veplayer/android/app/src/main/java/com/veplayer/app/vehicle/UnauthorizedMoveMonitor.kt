package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tow / unauthorized movement while secured → TTS + inbox.
 */
object UnauthorizedMoveMonitor {
    private val _state = MutableStateFlow(UnauthorizedMove.State())
    val state: StateFlow<UnauthorizedMove.State> = _state.asStateFlow()

    private var movingSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 800L
    private const val COOLDOWN_MS = 30_000L

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val ignOn =
            if (prefs.towSim) {
                false
            } else {
                IdleAlert.isIgnitionOn(signals.ignition)
            }
        val parking = if (prefs.towSim) true else signals.parkingBrake
        val speed =
            when {
                prefs.towSim && prefs.towSimKmh > 0f -> prefs.towSimKmh
                prefs.towSim -> signals.speedKmh.coerceAtLeast(8f)
                else -> signals.speedKmh
            }
        val secured = UnauthorizedMove.isSecured(ignOn, parking)
        val above = speed >= prefs.towSpeedMinKmh
        val movingForSec =
            if (prefs.towEnabled && secured && above) {
                if (movingSinceMs == 0L) movingSinceMs = nowMs
                ((nowMs - movingSinceMs) / 1000f).coerceAtLeast(0f)
            } else {
                movingSinceMs = 0L
                0f
            }
        val st =
            UnauthorizedMove.evaluate(
                ignitionOn = ignOn,
                parkingBrake = parking,
                speedKmh = speed,
                movingForSec = movingForSec,
                speedMinKmh = prefs.towSpeedMinKmh,
                warnSec = prefs.towWarnSec,
                alertSec = prefs.towAlertSec,
            )
        if (!prefs.towEnabled) {
            _state.value = st.copy(showWarn = false)
            return
        }
        _state.value = st

        if (!st.showWarn) {
            lastKey = ""
            return
        }
        val held = movingSinceMs > 0L && nowMs - movingSinceMs >= HOLD_MS
        val cooled = nowMs - lastSpokenMs >= COOLDOWN_MS
        val changed = st.band != lastKey
        if (held && (cooled || changed) && prefs.towTts) {
            lastSpokenMs = nowMs
            lastKey = st.band
            val phrase = UnauthorizedMove.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "tow_alert" else "tow_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "tow:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
