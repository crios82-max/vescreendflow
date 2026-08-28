package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HvDisLimitMonitor {
    private val _state = MutableStateFlow(HvDisLimit.State())
    val state: StateFlow<HvDisLimit.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val a = when {
            prefs.hvDisSimA > 0f -> prefs.hvDisSimA
            else -> signals.hvDisLimitA
        }
        val speed = when {
            prefs.hvDisSimA > 0f && prefs.hvDisSimSpeedKmh > 0f -> prefs.hvDisSimSpeedKmh
            prefs.hvDisSimA > 0f -> signals.speedKmh.coerceAtLeast(prefs.hvDisSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = HvDisLimit.evaluate(a, speed, prefs.hvDisWarnA, prefs.hvDisAlertA, prefs.hvDisSpeedMinKmh)
        if (!prefs.hvDisEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.currentA?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.hvDisTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = HvDisLimit.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "hv_dis_limit_alert" else "hv_dis_limit_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "hv_dis_limit:${st.band}:${nowMs / 60000}", false)
        }
    }
}
