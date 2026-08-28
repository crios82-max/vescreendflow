package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PmSensorB1Monitor {
    private val _state = MutableStateFlow(PmSensorB1.State())
    val state: StateFlow<PmSensorB1.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = when {
            prefs.pmB1SimPct > 0f -> prefs.pmB1SimPct
            else -> signals.pmSensorB1Pct
        }
        val speed = when {
            prefs.pmB1SimPct > 0f && prefs.pmB1SimSpeedKmh > 0f -> prefs.pmB1SimSpeedKmh
            prefs.pmB1SimPct > 0f -> signals.speedKmh.coerceAtLeast(prefs.pmB1SpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = PmSensorB1.evaluate(p, speed, prefs.pmB1WarnPct, prefs.pmB1AlertPct, prefs.pmB1SpeedMinKmh)
        if (!prefs.pmB1Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.pmPct ?: 0f).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.pmB1Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = PmSensorB1.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "pm_b1_alert" else "pm_b1_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "pm_b1:${st.band}:${nowMs / 60000}", false)
        }
    }
}
