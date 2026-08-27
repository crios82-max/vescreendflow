package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Accumulates stopped+ignition time; TTS + inbox on warn/alert.
 */
object IdleMonitor {
    private val _state =
        MutableStateFlow(
            IdleAlert.evaluate(0f, true, 0f),
        )
    val state: StateFlow<IdleAlert.State> = _state.asStateFlow()

    private var idleSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastSpokenBand = ""
    private const val COOLDOWN_MS = 5 * 60_000L

    fun tick(
        prefs: VePrefs,
        speedKmh: Float,
        ignition: IgnitionState,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val ignOn = IdleAlert.isIgnitionOn(ignition)
        val stopped = IdleAlert.isStopped(speedKmh, prefs.idleSpeedMaxKmh)
        val idleForSec =
            if (prefs.idleAlertEnabled && ignOn && stopped) {
                if (idleSinceMs == 0L) idleSinceMs = nowMs
                ((nowMs - idleSinceMs) / 1000f).coerceAtLeast(0f)
            } else {
                idleSinceMs = 0L
                0f
            }
        val st =
            IdleAlert.evaluate(
                speedKmh = speedKmh,
                ignitionOn = ignOn,
                idleForSec = idleForSec,
                warnSec = prefs.idleWarnSec.toFloat(),
                alertSec = prefs.idleAlertSec.toFloat(),
                speedMaxKmh = prefs.idleSpeedMaxKmh,
            )
        _state.value = st

        if (!prefs.idleAlertEnabled || !st.showWarn) {
            if (!st.showWarn) lastSpokenBand = ""
            return
        }
        val cooled = nowMs - lastSpokenMs >= COOLDOWN_MS
        val bandChanged = st.band != lastSpokenBand
        if ((cooled || bandChanged) && prefs.idleTtsWarn) {
            if (st.band == "warn" || st.band == "alert") {
                lastSpokenMs = nowMs
                lastSpokenBand = st.band
                val phrase = IdleAlert.voicePhrase(st)
                NavTts.speakNow(phrase)
                FleetInbox.push(
                    prefs = prefs,
                    kind = "idle_${st.band}",
                    text = phrase,
                    severity = if (st.band == "alert") "warn" else "info",
                    id = "idle:${st.band}:${nowMs / 300_000}",
                    speak = false,
                )
            }
        }
    }
}
