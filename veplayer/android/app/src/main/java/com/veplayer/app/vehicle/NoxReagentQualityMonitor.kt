package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NoxReagentQualityMonitor {
    private val _state = MutableStateFlow(NoxReagentQuality.State())
    val state: StateFlow<NoxReagentQuality.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val h = if (prefs.noxReqSimH > 0f) prefs.noxReqSimH else signals.noxReagentQualHours
        val st = NoxReagentQuality.evaluate(h, prefs.noxReqWarnH, prefs.noxReqAlertH)
        if (!prefs.noxReqEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.qualHours ?: 0f).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.noxReqTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = NoxReagentQuality.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "nox_req_alert" else "nox_req_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "nox_req:${st.band}:${nowMs / 60000}", false)
        }
    }
}
