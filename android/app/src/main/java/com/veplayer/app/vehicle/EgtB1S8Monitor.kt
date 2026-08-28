package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EgtB1S8Monitor {
    private val _state = MutableStateFlow(EgtB1S8.State())
    val state: StateFlow<EgtB1S8.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val c = if (prefs.egtB1s8SimC > 0f) prefs.egtB1s8SimC else signals.egtB1s8TempC
        val st = EgtB1S8.evaluate(c, prefs.egtB1s8WarnC, prefs.egtB1s8AlertC)
        if (!prefs.egtB1s8Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.egtTempC ?: 0f).toInt() / 20}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.egtB1s8Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = EgtB1S8.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "egt_b1s8_alert" else "egt_b1s8_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "egt_b1s8:${st.band}:${nowMs / 60000}", false)
        }
    }
}
