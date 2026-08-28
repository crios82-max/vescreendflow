package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High long-term fuel trim bank 2 while moving (OBD 0109) → TTS + inbox.
 */
object FuelTrimLtftB2Monitor {
    private val _state = MutableStateFlow(FuelTrimLtftB2.State())
    val state: StateFlow<FuelTrimLtftB2.State> = _state.asStateFlow()

    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 2_000L
    private const val COOLDOWN_MS = 30_000L

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val trim =
            when {
                prefs.ltftB2SimPct != 0f -> prefs.ltftB2SimPct
                else -> signals.fuelTrimLtftB2Pct
            }
        val speed =
            when {
                prefs.ltftB2SimPct != 0f && prefs.ltftB2SimSpeedKmh > 0f -> prefs.ltftB2SimSpeedKmh
                prefs.ltftB2SimPct != 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.ltftB2SpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            FuelTrimLtftB2.evaluate(
                trimPct = trim,
                speedKmh = speed,
                warnPct = prefs.ltftB2WarnPct,
                alertPct = prefs.ltftB2AlertPct,
                speedMinKmh = prefs.ltftB2SpeedMinKmh,
            )
        if (!prefs.ltftB2Enabled) {
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
        val key = "${st.band}:${(st.trimPct ?: 0f).toInt() / 3}"
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.ltftB2Tts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = FuelTrimLtftB2.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "ltft_b2_alert" else "ltft_b2_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "ltft_b2:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
