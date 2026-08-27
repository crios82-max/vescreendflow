package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.fleet.ShiftTracker
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Open-shift duration → fatigue warn/alert · TTS + inbox.
 */
object ShiftFatigueMonitor {
    private val _state = MutableStateFlow(ShiftFatigue.State())
    val state: StateFlow<ShiftFatigue.State> = _state.asStateFlow()

    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val COOLDOWN_MS = 20 * 60_000L

    fun tick(
        prefs: VePrefs,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val shift = ShiftTracker.shift.value
        val open = shift.status == "open" && shift.startedAt > 0L
        val elapsedSec =
            when {
                prefs.fatigueSimHours > 0f -> prefs.fatigueSimHours * 3600f
                open -> ((nowMs - shift.startedAt) / 1000f).coerceAtLeast(0f)
                else -> 0f
            }
        val st =
            ShiftFatigue.evaluate(
                open = open || prefs.fatigueSimHours > 0f,
                durationSec = elapsedSec,
                warnHours = prefs.fatigueWarnHours,
                alertHours = prefs.fatigueAlertHours,
            )
        if (!prefs.fatigueEnabled) {
            _state.value = st.copy(showWarn = false)
            return
        }
        _state.value = st

        if (!st.showWarn) {
            lastKey = ""
            return
        }
        val cooled = nowMs - lastSpokenMs >= COOLDOWN_MS
        val key = "${st.band}:${st.label}"
        val changed = key != lastKey
        if ((cooled || changed) && prefs.fatigueTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = ShiftFatigue.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "shift_fatigue" else "shift_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "fatigue:${st.band}:${nowMs / 600_000}",
                speak = false,
            )
        }
    }
}
