package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Forgotten hazard lights while moving → TTS + inbox.
 */
object HazardStuckMonitor {
    private val _state = MutableStateFlow(HazardStuck.State())
    val state: StateFlow<HazardStuck.State> = _state.asStateFlow()

    private var heldAccumMs = 0L
    private var lastTsMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val COOLDOWN_MS = 60_000L

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val simSec = prefs.hazardStuckSimSec
        val active =
            when {
                simSec > 0f -> true
                else -> signals.turn == TurnSignal.HAZARD
            }
        val moving =
            when {
                simSec > 0f -> true
                else -> signals.speedKmh >= prefs.hazardStuckSpeedMinKmh
            }

        if (!active || !moving) {
            heldAccumMs = 0L
            lastTsMs = nowMs
            _state.value = HazardStuck.State(band = "idle")
            lastKey = ""
            return
        }

        if (lastTsMs > 0L) {
            val dt = (nowMs - lastTsMs).coerceIn(0L, 5_000L)
            heldAccumMs += dt
        }
        lastTsMs = nowMs

        val heldSec = if (simSec > 0f) simSec else heldAccumMs / 1000f
        val st =
            HazardStuck.evaluate(
                active = true,
                heldSec = heldSec,
                warnSec = prefs.hazardStuckWarnSec,
                alertSec = prefs.hazardStuckAlertSec,
            )
        if (!prefs.hazardStuckEnabled) {
            _state.value = st.copy(showWarn = false)
            return
        }
        _state.value = st

        if (!st.showWarn) {
            lastKey = ""
            return
        }
        val cooled = nowMs - lastSpokenMs >= COOLDOWN_MS
        val key = "${st.band}:${(st.heldSec / 15).toInt()}"
        val changed = key != lastKey
        if ((cooled || changed) && prefs.hazardStuckTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = HazardStuck.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "hazard_stuck_alert" else "hazard_stuck_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "hazard:${st.band}:${nowMs / 180_000}",
                speak = false,
            )
        }
    }
}
