package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.fleet.ShiftTracker
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live eco score from open shift → TTS + inbox when score drops.
 */
object EcoLiveMonitor {
    private val _state = MutableStateFlow(EcoLive.State())
    val state: StateFlow<EcoLive.State> = _state.asStateFlow()

    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val COOLDOWN_MS = 5 * 60_000L

    fun tick(
        prefs: VePrefs,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (!prefs.ecoLiveEnabled) {
            _state.value = EcoLive.State()
            return
        }

        val sim = prefs.ecoLiveSimScore
        val shift = ShiftTracker.shift.value
        val shiftOpen = shift.status == "open"
        val active = shiftOpen || sim > 0f

        val score =
            when {
                sim > 0f -> sim.toInt()
                shiftOpen -> shift.ecoScore
                else -> null
            }
        val band =
            when {
                sim > 0f ->
                    when {
                        sim >= 80f -> "good"
                        sim >= 55f -> "fair"
                        else -> "poor"
                    }
                else -> shift.ecoBand.ifBlank { null }
            }

        val st =
            EcoLive.evaluate(
                score = score,
                band = band,
                warnScore = prefs.ecoLiveWarn.toInt(),
                alertScore = prefs.ecoLiveAlert.toInt(),
                active = active,
                idleSec = shift.idleSec,
                overspeedSec = shift.overspeedSec,
                absEvents = shift.absEvents,
            )
        _state.value = st

        if (!st.showWarn || !st.active) {
            lastKey = ""
            return
        }
        val cooled = nowMs - lastSpokenMs >= COOLDOWN_MS
        val key = "${st.band}:${st.score / 5}"
        val changed = key != lastKey
        if ((cooled || changed) && prefs.ecoLiveTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = EcoLive.voicePhrase(st)
            NavTts.speakNow(phrase)
            val kind = if (st.score <= prefs.ecoLiveAlert) "eco_alert" else "eco_warn"
            FleetInbox.push(
                prefs = prefs,
                kind = kind,
                text = phrase,
                severity = if (kind == "eco_alert") "critical" else "warn",
                id = "eco:${st.band}:${nowMs / 300_000}",
                speak = false,
            )
        }
    }
}
