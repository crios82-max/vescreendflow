package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Rolling in P/N → TTS + inbox.
 */
object GearRollMonitor {
    private val _state = MutableStateFlow(GearRoll.State())
    val state: StateFlow<GearRoll.State> = _state.asStateFlow()

    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 1_500L
    private const val COOLDOWN_MS = 25_000L

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val gear =
            when {
                prefs.gearRollSim ->
                    when (prefs.gearRollSimGear.uppercase()) {
                        "N" -> Gear.N
                        else -> Gear.P
                    }
                else -> signals.gear
            }
        val speed =
            when {
                prefs.gearRollSim && prefs.gearRollSimKmh > 0f -> prefs.gearRollSimKmh
                prefs.gearRollSim ->
                    signals.speedKmh.coerceAtLeast(prefs.gearRollWarnKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            GearRoll.evaluate(
                gear = gear,
                speedKmh = speed,
                warnKmh = prefs.gearRollWarnKmh,
                alertKmh = prefs.gearRollAlertKmh,
            )
        if (!prefs.gearRollEnabled) {
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
        val key = "${st.band}:${st.gear}"
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.gearRollTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = GearRoll.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "gear_roll_alert" else "gear_roll_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "gearroll:${st.band}:${st.gear}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
