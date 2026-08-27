package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ABS intervention → DriveViz + TTS + inbox.
 */
object AbsHudMonitor {
    private val _state = MutableStateFlow(AbsHud.State())
    val state: StateFlow<AbsHud.State> = _state.asStateFlow()

    private var activeSinceMs = 0L
    private var lastTsMs = 0L
    private var lastActive = false
    private var eventTimes = ArrayDeque<Long>()
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val COOLDOWN_MS = 30_000L
    private const val EVENT_WINDOW_MS = 60_000L

    /** One-shot: next tick forces ABS active. */
    fun armSim() {
        simArmed = true
    }

    private var simArmed = false

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        var active =
            when {
                prefs.absSim -> true
                simArmed -> true
                else -> signals.absActive
            }
        if (simArmed) simArmed = false

        if (active && !lastActive) {
            eventTimes.addLast(nowMs)
        }
        lastActive = active
        while (eventTimes.isNotEmpty() && nowMs - eventTimes.first() > EVENT_WINDOW_MS) {
            eventTimes.removeFirst()
        }

        val activeForSec =
            if (active) {
                if (activeSinceMs == 0L) activeSinceMs = nowMs
                ((nowMs - activeSinceMs) / 1000f).coerceAtLeast(0f)
            } else {
                activeSinceMs = 0L
                0f
            }
        lastTsMs = nowMs

        val st =
            AbsHud.evaluate(
                active = active,
                activeForSec = activeForSec,
                events = eventTimes.size,
                warnSec = prefs.absWarnSec,
                alertSec = prefs.absAlertSec,
                alertEvents = prefs.absAlertEvents.toInt(),
            )
        if (!prefs.absHudEnabled) {
            _state.value = st.copy(showWarn = false)
            return
        }
        _state.value = st

        if (!st.showWarn) {
            lastKey = ""
            return
        }
        val cooled = nowMs - lastSpokenMs >= COOLDOWN_MS
        val key = "${st.band}:${st.events}"
        val changed = key != lastKey
        if ((cooled || changed) && prefs.absHudTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = AbsHud.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "abs_alert" else "abs_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "abs:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
