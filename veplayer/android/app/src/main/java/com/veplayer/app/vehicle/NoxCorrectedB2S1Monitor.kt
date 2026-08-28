package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NoxCorrectedB2S1Monitor {
    private val _state = MutableStateFlow(NoxCorrectedB2S1.State())
    val state: StateFlow<NoxCorrectedB2S1.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val ppm = when {
            prefs.noxCorrB2s1Sim > 0f -> prefs.noxCorrB2s1Sim
            else -> signals.noxCorrectedB2s1Ppm
        }
        val speed = when {
            prefs.noxCorrB2s1Sim > 0f && prefs.noxCorrB2s1SimSpeedKmh > 0f -> prefs.noxCorrB2s1SimSpeedKmh
            prefs.noxCorrB2s1Sim > 0f -> signals.speedKmh.coerceAtLeast(prefs.noxCorrB2s1SpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = NoxCorrectedB2S1.evaluate(ppm, speed, prefs.noxCorrB2s1Warn, prefs.noxCorrB2s1Alert, prefs.noxCorrB2s1SpeedMinKmh)
        if (!prefs.noxCorrB2s1Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${((st.noxPpm ?: 0f) / 50).toInt()}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.noxCorrB2s1Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = NoxCorrectedB2S1.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "nox_corr_b2s1_alert" else "nox_corr_b2s1_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "nox_corr_b2s1:${st.band}:${nowMs / 60000}", false)
        }
    }
}
