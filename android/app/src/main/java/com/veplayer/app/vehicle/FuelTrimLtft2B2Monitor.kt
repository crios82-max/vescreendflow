package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FuelTrimLtft2B2Monitor {
    private val _state = MutableStateFlow(FuelTrimLtft2B2.State())
    val state: StateFlow<FuelTrimLtft2B2.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val t = if (prefs.ltft2B2SimPct != 0f) prefs.ltft2B2SimPct else signals.fuelTrimLtft2B2Pct
        val speed = if (prefs.ltft2B2SimPct != 0f && prefs.ltft2B2SimSpeedKmh > 0f) prefs.ltft2B2SimSpeedKmh
        else if (prefs.ltft2B2SimPct != 0f) signals.speedKmh.coerceAtLeast(prefs.ltft2B2SpeedMinKmh + 1f) else signals.speedKmh
        val st = FuelTrimLtft2B2.evaluate(t, speed, prefs.ltft2B2WarnPct, prefs.ltft2B2AlertPct, prefs.ltft2B2SpeedMinKmh)
        if (!prefs.ltft2B2Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.trimPct ?: 0f).toInt() / 4}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.ltft2B2Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = FuelTrimLtft2B2.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "ltft2_b2_alert" else "ltft2_b2_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "ltft2_b2:${st.band}:${nowMs / 60000}", false)
        }
    }
}
