package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watches vehicle speed vs limit; TTS + inbox on sustained overspeed.
 */
object SpeedHudMonitor {
    private val _state = MutableStateFlow(SpeedHud.evaluate(0f, 50))
    val state: StateFlow<SpeedHud.State> = _state.asStateFlow()

    private var overSinceMs = 0L
    private var lastSpokenMs = 0L
    private const val HOLD_MS = 2_500L
    private const val COOLDOWN_MS = 45_000L

    fun tick(prefs: VePrefs, speedKmh: Float) {
        if (!prefs.speedHudEnabled) {
            _state.value = SpeedHud.evaluate(speedKmh, prefs.speedLimitKmh, prefs.speedWarnMarginKmh)
            return
        }
        val st =
            SpeedHud.evaluate(
                speedKmh = speedKmh,
                limitKmh = prefs.speedLimitKmh,
                warnMarginKmh = prefs.speedWarnMarginKmh,
            )
        _state.value = st
        val now = System.currentTimeMillis()
        if (st.showWarn) {
            if (overSinceMs == 0L) overSinceMs = now
            val held = now - overSinceMs >= HOLD_MS
            val cooled = now - lastSpokenMs >= COOLDOWN_MS
            if (held && cooled && prefs.speedTtsWarn) {
                lastSpokenMs = now
                val phrase = SpeedHud.voicePhrase(st)
                NavTts.speakNow(phrase)
                FleetInbox.push(
                    prefs = prefs,
                    kind = "overspeed",
                    text = phrase,
                    severity = "warn",
                    id = "overspeed:${st.limitKmh}:${now / 60_000}",
                    speak = false,
                )
            }
        } else {
            overSinceMs = 0L
        }
    }
}
