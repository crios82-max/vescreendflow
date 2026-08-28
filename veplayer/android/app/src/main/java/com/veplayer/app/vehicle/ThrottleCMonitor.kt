package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThrottleCMonitor {
    private val _state = MutableStateFlow(ThrottleC.State())
    val state: StateFlow<ThrottleC.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val thr = when {
            prefs.thrCSimPct > 0f -> prefs.thrCSimPct
            else -> signals.throttleCPct
        }
        val speed = when {
            prefs.thrCSimPct > 0f && prefs.thrCSimSpeedKmh > 0f -> prefs.thrCSimSpeedKmh
            prefs.thrCSimPct > 0f -> signals.speedKmh.coerceAtLeast(prefs.thrCSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = ThrottleC.evaluate(thr, speed, prefs.thrCWarnPct, prefs.thrCAlertPct, prefs.thrCSpeedMinKmh)
        if (!prefs.thrCEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.throttlePct ?: 0f).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.thrCTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = ThrottleC.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "thr_c_alert" else "thr_c_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "thr_c:${st.band}:${nowMs / 60000}", false)
        }
    }
}
