package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BcapReadyMonitor {
    private val _state = MutableStateFlow(BcapReady.State())
    val state: StateFlow<BcapReady.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val ready = when {
            prefs.bcapReadySim > 0 -> prefs.bcapReadySim == 1
            signals.bcapReady == null -> null
            else -> signals.bcapReady == 1
        }
        val st = BcapReady.evaluate(ready)
        if (!prefs.bcapReadyEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = st.band
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.bcapReadyTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = BcapReady.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, "bcap_ready_warn", phrase, "warn", "bcap_ready:${nowMs / 60000}", false)
        }
    }
}
