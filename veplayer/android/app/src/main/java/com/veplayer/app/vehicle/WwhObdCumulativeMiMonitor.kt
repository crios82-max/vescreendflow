package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object WwhObdCumulativeMiMonitor {
    private val _state = MutableStateFlow(WwhObdCumulativeMi.State())
    val state: StateFlow<WwhObdCumulativeMi.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val h = if (prefs.wwhCumMiSimH > 0f) prefs.wwhCumMiSimH else signals.wwhObdCumulativeMiHours
        val st = WwhObdCumulativeMi.evaluate(h, prefs.wwhCumMiWarnH, prefs.wwhCumMiAlertH)
        if (!prefs.wwhCumMiEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.miHours ?: 0f).toInt() / 10}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.wwhCumMiTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = WwhObdCumulativeMi.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "wwh_cumulative_mi_alert" else "wwh_cumulative_mi_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "wwh_cumulative_mi:${st.band}:${nowMs / 60000}", false)
        }
    }
}
