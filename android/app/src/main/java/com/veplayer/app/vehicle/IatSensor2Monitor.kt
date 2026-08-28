package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object IatSensor2Monitor {
    private val _state = MutableStateFlow(IatSensor2.State())
    val state: StateFlow<IatSensor2.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val c = if (prefs.iat2SimC > 0f) prefs.iat2SimC else signals.iatSensor2C
        val st = IatSensor2.evaluate(c, signals.speedKmh, prefs.iat2WarnC, prefs.iat2AlertC, prefs.iat2SpeedMinKmh)
        if (!prefs.iat2Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.tempC ?: 0f).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.iat2Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = IatSensor2.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "iat2_alert" else "iat2_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "iat2:${st.band}:${nowMs / 60000}", false)
        }
    }
}
