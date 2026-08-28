package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AuxInputStatusMonitor {
    private val _state = MutableStateFlow(AuxInputStatus.State())
    val state: StateFlow<AuxInputStatus.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val code = when {
            prefs.auxInputSimCode > 0 -> prefs.auxInputSimCode
            else -> signals.auxInputStatus
        }
        val st = AuxInputStatus.evaluate(code, signals.speedKmh, prefs.auxInputAlertMask, prefs.auxInputSpeedMinKmh)
        if (!prefs.auxInputEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "alert:${st.statusCode ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 60000 || key != lastKey) && prefs.auxInputTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = AuxInputStatus.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, "aux_input_alert", phrase, "critical", "aux_input:${st.statusCode}:${nowMs / 60000}", false)
        }
    }
}
