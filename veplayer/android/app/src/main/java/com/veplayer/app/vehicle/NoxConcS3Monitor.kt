package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NoxConcS3Monitor {
    private val _state = MutableStateFlow(NoxConcS3.State())
    val state: StateFlow<NoxConcS3.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val ppm = when {
            prefs.noxConcS3Sim > 0f -> prefs.noxConcS3Sim
            else -> signals.noxConcS3Ppm
        }
        val speed = when {
            prefs.noxConcS3Sim > 0f && prefs.noxConcS3SimSpeedKmh > 0f -> prefs.noxConcS3SimSpeedKmh
            prefs.noxConcS3Sim > 0f -> signals.speedKmh.coerceAtLeast(prefs.noxConcS3SpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = NoxConcS3.evaluate(ppm, speed, prefs.noxConcS3Warn, prefs.noxConcS3Alert, prefs.noxConcS3SpeedMinKmh)
        if (!prefs.noxConcS3Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${((st.noxPpm ?: 0f) / 50).toInt()}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.noxConcS3Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = NoxConcS3.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "nox_conc_s3_alert" else "nox_conc_s3_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "nox_conc_s3:${st.band}:${nowMs / 60000}", false)
        }
    }
}
