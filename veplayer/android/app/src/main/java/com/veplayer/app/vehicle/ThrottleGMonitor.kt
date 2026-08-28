package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThrottleGMonitor {
    private val _state = MutableStateFlow(ThrottleG.State())
    val state: StateFlow<ThrottleG.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val thr = when {
            prefs.thrGSimPct > 0f -> prefs.thrGSimPct
            else -> signals.throttleGPct
        }
        val speed = when {
            prefs.thrGSimPct > 0f && prefs.thrGSimSpeedKmh > 0f -> prefs.thrGSimSpeedKmh
            prefs.thrGSimPct > 0f -> signals.speedKmh.coerceAtLeast(prefs.thrGSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = ThrottleG.evaluate(thr, speed, prefs.thrGWarnPct, prefs.thrGAlertPct, prefs.thrGSpeedMinKmh)
        if (!prefs.thrGEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.throttlePct ?: 0f).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.thrGTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = ThrottleG.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "thr_g_alert" else "thr_g_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "thr_g:${st.band}:${nowMs / 60000}", false)
        }
    }
}
