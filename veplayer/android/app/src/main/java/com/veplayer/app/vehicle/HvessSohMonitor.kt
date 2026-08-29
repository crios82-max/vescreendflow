package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HvessSohMonitor {
    private val _state = MutableStateFlow(HvessSoh.State())
    val state: StateFlow<HvessSoh.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = if (prefs.hvessSohSimPct > 0f) prefs.hvessSohSimPct else signals.hvessSohPct
        val st = HvessSoh.evaluate(p, prefs.hvessSohWarnPct, prefs.hvessSohAlertPct)
        if (!prefs.hvessSohEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.sohPct?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.hvessSohTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = HvessSoh.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "hvess_soh_alert" else "hvess_soh_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "hvess_soh:${st.band}:${nowMs / 60000}", false)
        }
    }
}
