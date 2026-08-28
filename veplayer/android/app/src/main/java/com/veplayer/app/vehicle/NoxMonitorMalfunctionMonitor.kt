package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NoxMonitorMalfunctionMonitor {
    private val _state = MutableStateFlow(NoxMonitorMalfunction.State())
    val state: StateFlow<NoxMonitorMalfunction.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val h = if (prefs.noxMalSimH > 0f) prefs.noxMalSimH else signals.noxMonitorMalfunctionHours
        val st = NoxMonitorMalfunction.evaluate(h, prefs.noxMalWarnH, prefs.noxMalAlertH)
        if (!prefs.noxMalEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.malfHours ?: 0f).toInt() / 10}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.noxMalTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = NoxMonitorMalfunction.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "nox_monitor_malf_alert" else "nox_monitor_malf_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "nox_monitor_malf:${st.band}:${nowMs / 60000}", false)
        }
    }
}
