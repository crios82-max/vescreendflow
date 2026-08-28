package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PmSensorB2Monitor {
    private val _state = MutableStateFlow(PmSensorB2.State())
    val state: StateFlow<PmSensorB2.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = when {
            prefs.pmB2SimPct > 0f -> prefs.pmB2SimPct
            else -> signals.pmSensorB2Pct
        }
        val speed = when {
            prefs.pmB2SimPct > 0f && prefs.pmB2SimSpeedKmh > 0f -> prefs.pmB2SimSpeedKmh
            prefs.pmB2SimPct > 0f -> signals.speedKmh.coerceAtLeast(prefs.pmB2SpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = PmSensorB2.evaluate(p, speed, prefs.pmB2WarnPct, prefs.pmB2AlertPct, prefs.pmB2SpeedMinKmh)
        if (!prefs.pmB2Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.pmPct ?: 0f).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.pmB2Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = PmSensorB2.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "pm_b2_alert" else "pm_b2_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "pm_b2:${st.band}:${nowMs / 60000}", false)
        }
    }
}
