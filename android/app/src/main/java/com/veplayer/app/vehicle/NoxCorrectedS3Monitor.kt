package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NoxCorrectedS3Monitor {
    private val _state = MutableStateFlow(NoxCorrectedS3.State())
    val state: StateFlow<NoxCorrectedS3.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val ppm = when {
            prefs.noxCorrS3Sim > 0f -> prefs.noxCorrS3Sim
            else -> signals.noxCorrectedS3Ppm
        }
        val speed = when {
            prefs.noxCorrS3Sim > 0f && prefs.noxCorrS3SimSpeedKmh > 0f -> prefs.noxCorrS3SimSpeedKmh
            prefs.noxCorrS3Sim > 0f -> signals.speedKmh.coerceAtLeast(prefs.noxCorrS3SpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = NoxCorrectedS3.evaluate(ppm, speed, prefs.noxCorrS3Warn, prefs.noxCorrS3Alert, prefs.noxCorrS3SpeedMinKmh)
        if (!prefs.noxCorrS3Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${((st.noxPpm ?: 0f) / 50).toInt()}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.noxCorrS3Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = NoxCorrectedS3.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "nox_corr_s3_alert" else "nox_corr_s3_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "nox_corr_s3:${st.band}:${nowMs / 60000}", false)
        }
    }
}
