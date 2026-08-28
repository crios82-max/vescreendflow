package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HvBalHoursMonitor {
    private val _state = MutableStateFlow(HvBalHours.State())
    val state: StateFlow<HvBalHours.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val h = if (prefs.hvBalSimH > 0f) prefs.hvBalSimH else signals.hvBalHours
        val st = HvBalHours.evaluate(h, prefs.hvBalWarnH, prefs.hvBalAlertH)
        if (!prefs.hvBalEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.hours?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.hvBalTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = HvBalHours.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "hv_bal_hours_alert" else "hv_bal_hours_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "hv_bal_hours:${st.band}:${nowMs / 60000}", false)
        }
    }
}
