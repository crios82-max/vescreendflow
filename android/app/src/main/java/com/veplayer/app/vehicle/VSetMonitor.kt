package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VSetMonitor {
    private val _state = MutableStateFlow(VSet.State())
    val state: StateFlow<VSet.State> = _state.asStateFlow()
    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = if (prefs.vSetSimKmh > 0f) prefs.vSetSimKmh else signals.vSetKmh
        val st = VSet.evaluate(p, prefs.vSetWarnKmh, prefs.vSetAlertKmh)
        if (!prefs.vSetEnabled) {
            _state.value = st.copy(showWarn = false)
            return
        }
        _state.value = st
        if (!st.showWarn) {
            warnSinceMs = 0L
            lastKey = ""
            return
        }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.kmh?.toInt() ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.vSetTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = VSet.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs,
                if (st.band == "alert") "v_set_alert" else "v_set_warn",
                phrase,
                if (st.band == "alert") "critical" else "warn",
                "v_set:${st.band}:${nowMs / 60000}",
                false,
            )
        }
    }
}
