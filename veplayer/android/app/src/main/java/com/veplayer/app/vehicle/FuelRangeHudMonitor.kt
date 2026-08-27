package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watches fuel/SOC/range; TTS + inbox on sustained low level.
 */
object FuelRangeHudMonitor {
    private val _state =
        MutableStateFlow(
            FuelRangeHud.evaluate(null, null, null),
        )
    val state: StateFlow<FuelRangeHud.State> = _state.asStateFlow()

    private var lowSinceMs = 0L
    private var lastSpokenMs = 0L
    private const val HOLD_MS = 3_000L
    private const val COOLDOWN_MS = 10 * 60_000L

    fun tick(
        prefs: VePrefs,
        fuelPct: Float?,
        socPct: Float?,
        rangeKm: Float?,
    ) {
        val st =
            FuelRangeHud.evaluate(
                fuelPct = fuelPct,
                socPct = socPct,
                rangeKm = rangeKm,
                warnPct = prefs.fuelWarnPct,
                criticalPct = prefs.fuelCriticalPct,
                warnRangeKm = prefs.rangeWarnKm,
                criticalRangeKm = prefs.rangeCriticalKm,
            )
        if (!prefs.fuelHudEnabled) {
            _state.value = st
            return
        }
        _state.value = st
        val now = System.currentTimeMillis()
        if (st.showWarn) {
            if (lowSinceMs == 0L) lowSinceMs = now
            val held = now - lowSinceMs >= HOLD_MS
            val cooled = now - lastSpokenMs >= COOLDOWN_MS
            if (held && cooled && prefs.fuelTtsWarn) {
                lastSpokenMs = now
                val phrase = FuelRangeHud.voicePhrase(st)
                NavTts.speakNow(phrase)
                FleetInbox.push(
                    prefs = prefs,
                    kind = "fuel_low",
                    text = phrase,
                    severity = "warn",
                    id = "fuel_low:${st.kind}:${now / 600_000}",
                    speak = false,
                )
            }
        } else {
            lowSinceMs = 0L
        }
    }
}
