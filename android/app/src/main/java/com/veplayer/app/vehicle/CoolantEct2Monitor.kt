package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CoolantEct2Monitor {
    private val _state = MutableStateFlow(CoolantEct2.State())
    val state: StateFlow<CoolantEct2.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val c = if (prefs.ect2SimC > 0f) prefs.ect2SimC else signals.coolantEct2C
        val st = CoolantEct2.evaluate(c, prefs.ect2WarnC, prefs.ect2AlertC)
        if (!prefs.ect2Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.coolantC ?: 0f).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 45000 || key != lastKey) && prefs.ect2Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = CoolantEct2.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "ect2_alert" else "ect2_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "ect2:${st.band}:${nowMs / 60000}", false)
        }
    }
}
