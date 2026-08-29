package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HvCurrRateMonitor {
    private val _state = MutableStateFlow(HvCurrRate.State())
    val state: StateFlow<HvCurrRate.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = if (prefs.hvCurrRateSimAhs != 0f) prefs.hvCurrRateSimAhs else signals.hvCurrRateAhs
        val st = HvCurrRate.evaluate(p, prefs.hvCurrRateWarnAhs, prefs.hvCurrRateAlertAhs)
        if (!prefs.hvCurrRateEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.ahs?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.hvCurrRateTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = HvCurrRate.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "hv_curr_rate_alert" else "hv_curr_rate_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "hv_curr_rate:${st.band}:${nowMs / 60000}", false)
        }
    }
}
