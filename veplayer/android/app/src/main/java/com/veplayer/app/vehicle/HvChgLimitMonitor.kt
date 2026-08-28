package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HvChgLimitMonitor {
    private val _state = MutableStateFlow(HvChgLimit.State())
    val state: StateFlow<HvChgLimit.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val a = when {
            prefs.hvChgSimA > 0f -> prefs.hvChgSimA
            else -> signals.hvChgLimitA
        }
        val speed = when {
            prefs.hvChgSimA > 0f && prefs.hvChgSimSpeedKmh > 0f -> prefs.hvChgSimSpeedKmh
            prefs.hvChgSimA > 0f -> signals.speedKmh.coerceAtLeast(prefs.hvChgSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = HvChgLimit.evaluate(a, speed, prefs.hvChgWarnA, prefs.hvChgAlertA, prefs.hvChgSpeedMinKmh)
        if (!prefs.hvChgEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.currentA?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.hvChgTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = HvChgLimit.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "hv_chg_limit_alert" else "hv_chg_limit_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "hv_chg_limit:${st.band}:${nowMs / 60000}", false)
        }
    }
}
