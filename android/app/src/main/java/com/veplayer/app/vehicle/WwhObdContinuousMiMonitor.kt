package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object WwhObdContinuousMiMonitor {
    private val _state = MutableStateFlow(WwhObdContinuousMi.State())
    val state: StateFlow<WwhObdContinuousMi.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val h = if (prefs.wwhContMiSimH > 0f) prefs.wwhContMiSimH else signals.wwhObdContinuousMiHours
        val st = WwhObdContinuousMi.evaluate(h, prefs.wwhContMiWarnH, prefs.wwhContMiAlertH)
        if (!prefs.wwhContMiEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.miHours ?: 0f).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.wwhContMiTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = WwhObdContinuousMi.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "wwh_continuous_mi_alert" else "wwh_continuous_mi_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "wwh_continuous_mi:${st.band}:${nowMs / 60000}", false)
        }
    }
}
