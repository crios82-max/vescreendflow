package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Forgotten turn signal while moving → TTS + inbox.
 */
object TurnStuckMonitor {
    private val _state = MutableStateFlow(TurnStuck.State())
    val state: StateFlow<TurnStuck.State> = _state.asStateFlow()

    private var heldAccumMs = 0L
    private var activeSide = ""
    private var lastTsMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val COOLDOWN_MS = 45_000L

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val simSec = prefs.turnStuckSimSec
        val side =
            when {
                simSec > 0f && prefs.turnStuckSimSide.isNotBlank() ->
                    prefs.turnStuckSimSide.lowercase()
                simSec > 0f -> "left"
                signals.turn == TurnSignal.LEFT -> "left"
                signals.turn == TurnSignal.RIGHT -> "right"
                else -> ""
            }
        val moving =
            when {
                simSec > 0f -> true
                else -> signals.speedKmh >= prefs.turnStuckSpeedMinKmh
            }
        val steerCancel =
            simSec <= 0f &&
                signals.steeringAngleDeg != null &&
                abs(signals.steeringAngleDeg!!) >= prefs.turnStuckSteerCancelDeg

        if (side.isEmpty() || !moving || steerCancel || signals.turn == TurnSignal.HAZARD) {
            heldAccumMs = 0L
            activeSide = ""
            lastTsMs = nowMs
            _state.value = TurnStuck.State(band = "idle")
            lastKey = ""
            return
        }

        if (side != activeSide) {
            activeSide = side
            heldAccumMs = 0L
        }
        if (lastTsMs > 0L) {
            val dt = (nowMs - lastTsMs).coerceIn(0L, 5_000L)
            heldAccumMs += dt
        }
        lastTsMs = nowMs

        val heldSec = if (simSec > 0f) simSec else heldAccumMs / 1000f
        val st =
            TurnStuck.evaluate(
                side = side,
                heldSec = heldSec,
                warnSec = prefs.turnStuckWarnSec,
                alertSec = prefs.turnStuckAlertSec,
            )
        if (!prefs.turnStuckEnabled) {
            _state.value = st.copy(showWarn = false)
            return
        }
        _state.value = st

        if (!st.showWarn) {
            lastKey = ""
            return
        }
        val cooled = nowMs - lastSpokenMs >= COOLDOWN_MS
        val key = "${st.band}:${st.side}:${(st.heldSec / 10).toInt()}"
        val changed = key != lastKey
        if ((cooled || changed) && prefs.turnStuckTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = TurnStuck.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "turn_stuck_alert" else "turn_stuck_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "turnstuck:${st.band}:${st.side}:${nowMs / 120_000}",
                speak = false,
            )
        }
    }
}
