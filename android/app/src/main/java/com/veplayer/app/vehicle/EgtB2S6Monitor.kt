package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EgtB2S6Monitor {
    private val _state = MutableStateFlow(EgtB2S6.State())
    val state: StateFlow<EgtB2S6.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val c = if (prefs.egtB2s6SimC > 0f) prefs.egtB2s6SimC else signals.egtB2s6TempC
        val st = EgtB2S6.evaluate(c, prefs.egtB2s6WarnC, prefs.egtB2s6AlertC)
        if (!prefs.egtB2s6Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.egtTempC ?: 0f).toInt() / 20}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.egtB2s6Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = EgtB2S6.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "egt_b2s6_alert" else "egt_b2s6_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "egt_b2s6:${st.band}:${nowMs / 60000}", false)
        }
    }
}
