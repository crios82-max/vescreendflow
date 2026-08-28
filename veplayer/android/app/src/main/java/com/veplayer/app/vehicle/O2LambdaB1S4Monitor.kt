package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object O2LambdaB1S4Monitor {
    private val _state = MutableStateFlow(O2LambdaB1S4.State())
    val state: StateFlow<O2LambdaB1S4.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val l = when {
            prefs.o2LambdaB1s4Sim > 0f -> prefs.o2LambdaB1s4Sim
            else -> signals.o2LambdaB1s4
        }
        val speed = when {
            prefs.o2LambdaB1s4Sim > 0f && prefs.o2LambdaB1s4SimSpeedKmh > 0f -> prefs.o2LambdaB1s4SimSpeedKmh
            prefs.o2LambdaB1s4Sim > 0f -> signals.speedKmh.coerceAtLeast(prefs.o2LambdaB1s4SpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = O2LambdaB1S4.evaluate(l, speed, prefs.o2LambdaB1s4Warn, prefs.o2LambdaB1s4Alert, prefs.o2LambdaB1s4SpeedMinKmh)
        if (!prefs.o2LambdaB1s4Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${((st.lambda ?: 0f) * 100).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.o2LambdaB1s4Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = O2LambdaB1S4.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "o2_lmb_b1s4_alert" else "o2_lmb_b1s4_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "o2_lmb_b1s4:${st.band}:${nowMs / 60000}", false)
        }
    }
}
