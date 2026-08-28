package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NoxCorrectedB2S2Monitor {
    private val _state = MutableStateFlow(NoxCorrectedB2S2.State())
    val state: StateFlow<NoxCorrectedB2S2.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val ppm = when {
            prefs.noxCorrB2s2Sim > 0f -> prefs.noxCorrB2s2Sim
            else -> signals.noxCorrectedB2s2Ppm
        }
        val speed = when {
            prefs.noxCorrB2s2Sim > 0f && prefs.noxCorrB2s2SimSpeedKmh > 0f -> prefs.noxCorrB2s2SimSpeedKmh
            prefs.noxCorrB2s2Sim > 0f -> signals.speedKmh.coerceAtLeast(prefs.noxCorrB2s2SpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = NoxCorrectedB2S2.evaluate(ppm, speed, prefs.noxCorrB2s2Warn, prefs.noxCorrB2s2Alert, prefs.noxCorrB2s2SpeedMinKmh)
        if (!prefs.noxCorrB2s2Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${((st.noxPpm ?: 0f) / 50).toInt()}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.noxCorrB2s2Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = NoxCorrectedB2S2.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "nox_corr_b2s2_alert" else "nox_corr_b2s2_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "nox_corr_b2s2:${st.band}:${nowMs / 60000}", false)
        }
    }
}
