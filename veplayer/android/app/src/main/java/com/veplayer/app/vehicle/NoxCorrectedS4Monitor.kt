package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NoxCorrectedS4Monitor {
    private val _state = MutableStateFlow(NoxCorrectedS4.State())
    val state: StateFlow<NoxCorrectedS4.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val ppm = when {
            prefs.noxCorrS4Sim > 0f -> prefs.noxCorrS4Sim
            else -> signals.noxCorrectedS4Ppm
        }
        val speed = when {
            prefs.noxCorrS4Sim > 0f && prefs.noxCorrS4SimSpeedKmh > 0f -> prefs.noxCorrS4SimSpeedKmh
            prefs.noxCorrS4Sim > 0f -> signals.speedKmh.coerceAtLeast(prefs.noxCorrS4SpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = NoxCorrectedS4.evaluate(ppm, speed, prefs.noxCorrS4Warn, prefs.noxCorrS4Alert, prefs.noxCorrS4SpeedMinKmh)
        if (!prefs.noxCorrS4Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${((st.noxPpm ?: 0f) / 50).toInt()}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.noxCorrS4Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = NoxCorrectedS4.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "nox_corr_s4_alert" else "nox_corr_s4_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "nox_corr_s4:${st.band}:${nowMs / 60000}", false)
        }
    }
}
