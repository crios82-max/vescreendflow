package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HvessCurrentMonitor {
    private val _state = MutableStateFlow(HvessCurrent.State())
    val state: StateFlow<HvessCurrent.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val a = when {
            prefs.hvessCurSimA != 0f -> prefs.hvessCurSimA
            else -> signals.hvessCurrentA
        }
        val speed = when {
            prefs.hvessCurSimA != 0f && prefs.hvessCurSimSpeedKmh > 0f -> prefs.hvessCurSimSpeedKmh
            prefs.hvessCurSimA != 0f -> signals.speedKmh.coerceAtLeast(prefs.hvessCurSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = HvessCurrent.evaluate(a, speed, prefs.hvessCurWarnA, prefs.hvessCurAlertA, prefs.hvessCurSpeedMinKmh)
        if (!prefs.hvessCurEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.currentA?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.hvessCurTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = HvessCurrent.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "hvess_current_alert" else "hvess_current_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "hvess_current:${st.band}:${nowMs / 60000}", false)
        }
    }
}
