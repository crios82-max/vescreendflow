package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MaxMafGpsMonitor {
    private val _state = MutableStateFlow(MaxMafGps.State())
    val state: StateFlow<MaxMafGps.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val g = if (prefs.maxMafSimGps > 0f) prefs.maxMafSimGps else signals.maxMafGps
        val st = MaxMafGps.evaluate(g, prefs.maxMafWarnLowGps, prefs.maxMafAlertLowGps)
        if (!prefs.maxMafEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.mafGps ?: 0f).toInt()}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.maxMafTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = MaxMafGps.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "max_maf_alert" else "max_maf_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "max_maf:${st.band}:${nowMs / 60000}", false)
        }
    }
}
