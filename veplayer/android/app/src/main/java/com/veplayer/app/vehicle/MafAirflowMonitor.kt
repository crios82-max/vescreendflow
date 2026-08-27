package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High MAF while moving (OBD 0110) → TTS + inbox.
 */
object MafAirflowMonitor {
    private val _state = MutableStateFlow(MafAirflow.State())
    val state: StateFlow<MafAirflow.State> = _state.asStateFlow()

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
        val gps =
            when {
                prefs.mafSimGps > 0f -> prefs.mafSimGps
                else -> signals.mafGps
            }
        val speed =
            when {
                prefs.mafSimGps > 0f && prefs.mafSimSpeedKmh > 0f -> prefs.mafSimSpeedKmh
                prefs.mafSimGps > 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.mafSpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            MafAirflow.evaluate(
                mafGps = gps,
                speedKmh = speed,
                warnGps = prefs.mafWarnGps,
                alertGps = prefs.mafAlertGps,
                speedMinKmh = prefs.mafSpeedMinKmh,
            )
        if (!prefs.mafEnabled) {
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
        val key = "${st.band}:${(st.mafGps ?: 0f).toInt() / 5}"
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.mafTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = MafAirflow.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "maf_alert" else "maf_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "maf:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
