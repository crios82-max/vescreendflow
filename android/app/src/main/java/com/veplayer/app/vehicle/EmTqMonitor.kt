package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EmTqMonitor {
    private val _state = MutableStateFlow(EmTq.State())
    val state: StateFlow<EmTq.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = if (prefs.emTqSimNm != 0f) prefs.emTqSimNm else signals.emTqANm
        val st = EmTq.evaluate(p, prefs.emTqWarnNm, prefs.emTqAlertNm)
        if (!prefs.emTqEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.nm?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.emTqTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = EmTq.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "em_tq_alert" else "em_tq_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "em_tq:${st.band}:${nowMs / 60000}", false)
        }
    }
}
