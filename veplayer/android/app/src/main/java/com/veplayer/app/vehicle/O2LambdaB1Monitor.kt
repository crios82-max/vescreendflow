package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object O2LambdaB1Monitor {
    private val _state = MutableStateFlow(O2LambdaB1.State())
    val state: StateFlow<O2LambdaB1.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val l = when {
            prefs.o2LambdaSim > 0f -> prefs.o2LambdaSim
            else -> signals.o2LambdaB1
        }
        val speed = when {
            prefs.o2LambdaSim > 0f && prefs.o2LambdaSimSpeedKmh > 0f -> prefs.o2LambdaSimSpeedKmh
            prefs.o2LambdaSim > 0f -> signals.speedKmh.coerceAtLeast(prefs.o2LambdaSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = O2LambdaB1.evaluate(l, speed, prefs.o2LambdaWarn, prefs.o2LambdaAlert, prefs.o2LambdaSpeedMinKmh)
        if (!prefs.o2LambdaEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${((st.lambda ?: 0f) * 100).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.o2LambdaTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = O2LambdaB1.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "o2_lambda_alert" else "o2_lambda_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "o2_lambda:${st.band}:${nowMs / 60000}", false)
        }
    }
}
