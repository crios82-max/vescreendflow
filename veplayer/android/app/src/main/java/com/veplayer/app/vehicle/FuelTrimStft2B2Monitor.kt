package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FuelTrimStft2B2Monitor {
    private val _state = MutableStateFlow(FuelTrimStft2B2.State())
    val state: StateFlow<FuelTrimStft2B2.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val t = if (prefs.stft2B2SimPct != 0f) prefs.stft2B2SimPct else signals.fuelTrimStft2B2Pct
        val speed = if (prefs.stft2B2SimPct != 0f && prefs.stft2B2SimSpeedKmh > 0f) prefs.stft2B2SimSpeedKmh
        else if (prefs.stft2B2SimPct != 0f) signals.speedKmh.coerceAtLeast(prefs.stft2B2SpeedMinKmh + 1f) else signals.speedKmh
        val st = FuelTrimStft2B2.evaluate(t, speed, prefs.stft2B2WarnPct, prefs.stft2B2AlertPct, prefs.stft2B2SpeedMinKmh)
        if (!prefs.stft2B2Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.trimPct ?: 0f).toInt() / 4}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.stft2B2Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = FuelTrimStft2B2.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "stft2_b2_alert" else "stft2_b2_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "stft2_b2:${st.band}:${nowMs / 60000}", false)
        }
    }
}
