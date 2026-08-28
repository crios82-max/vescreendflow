package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThrottleBMonitor {
    private val _state = MutableStateFlow(ThrottleB.State())
    val state: StateFlow<ThrottleB.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val thr = when {
            prefs.thrBSimPct > 0f -> prefs.thrBSimPct
            else -> signals.throttleBPct
        }
        val speed = when {
            prefs.thrBSimPct > 0f && prefs.thrBSimSpeedKmh > 0f -> prefs.thrBSimSpeedKmh
            prefs.thrBSimPct > 0f -> signals.speedKmh.coerceAtLeast(prefs.thrBSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = ThrottleB.evaluate(thr, speed, prefs.thrBWarnPct, prefs.thrBAlertPct, prefs.thrBSpeedMinKmh)
        if (!prefs.thrBEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.throttlePct ?: 0f).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.thrBTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = ThrottleB.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "thr_b_alert" else "thr_b_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "thr_b:${st.band}:${nowMs / 60000}", false)
        }
    }
}
