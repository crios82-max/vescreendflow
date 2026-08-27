package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EvapPurgeMonitor {
    private val _state = MutableStateFlow(EvapPurge.State())
    val state: StateFlow<EvapPurge.State> = _state.asStateFlow()
    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 2_000L
    private const val COOLDOWN_MS = 30_000L

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val pct =
            when {
                prefs.evapPurgeSimPct > 0f -> prefs.evapPurgeSimPct
                else -> signals.evapPurgePct
            }
        val speed =
            when {
                prefs.evapPurgeSimPct > 0f && prefs.evapPurgeSimSpeedKmh > 0f ->
                    prefs.evapPurgeSimSpeedKmh
                prefs.evapPurgeSimPct > 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.evapPurgeSpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            EvapPurge.evaluate(
                purgePct = pct,
                speedKmh = speed,
                warnPct = prefs.evapPurgeWarnPct,
                alertPct = prefs.evapPurgeAlertPct,
                speedMinKmh = prefs.evapPurgeSpeedMinKmh,
            )
        if (!prefs.evapPurgeEnabled) {
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
        val key = "${st.band}:${(st.purgePct ?: 0f).toInt() / 5}"
        if (held && (cooled || key != lastKey) && prefs.evapPurgeTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = EvapPurge.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "evap_purge_alert" else "evap_purge_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "evap_purge:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
