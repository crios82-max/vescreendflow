package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EgtB1S6Monitor {
    private val _state = MutableStateFlow(EgtB1S6.State())
    val state: StateFlow<EgtB1S6.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val c = if (prefs.egtB1s6SimC > 0f) prefs.egtB1s6SimC else signals.egtB1s6TempC
        val st = EgtB1S6.evaluate(c, prefs.egtB1s6WarnC, prefs.egtB1s6AlertC)
        if (!prefs.egtB1s6Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.egtTempC ?: 0f).toInt() / 20}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.egtB1s6Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = EgtB1S6.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "egt_b1s6_alert" else "egt_b1s6_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "egt_b1s6:${st.band}:${nowMs / 60000}", false)
        }
    }
}
