package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CatalystB1S4Monitor {
    private val _state = MutableStateFlow(CatalystB1S4.State())
    val state: StateFlow<CatalystB1S4.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val c = if (prefs.catB1s4SimC > 0f) prefs.catB1s4SimC else signals.catalystB1s4TempC
        val st = CatalystB1S4.evaluate(c, prefs.catB1s4WarnC, prefs.catB1s4AlertC)
        if (!prefs.catB1s4Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.catalystTempC ?: 0f).toInt() / 20}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.catB1s4Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = CatalystB1S4.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "cat_b1s4_alert" else "cat_b1s4_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "cat_b1s4:${st.band}:${nowMs / 60000}", false)
        }
    }
}
