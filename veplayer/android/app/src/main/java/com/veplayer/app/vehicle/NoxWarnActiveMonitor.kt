package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NoxWarnActiveMonitor {
    private val _state = MutableStateFlow(NoxWarnActive.State())
    val state: StateFlow<NoxWarnActive.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val active = when {
            prefs.noxWarnSim -> true
            signals.noxWarningActive == 1 -> true
            else -> false
        }
        val speed = when {
            prefs.noxWarnSim && prefs.noxWarnSimSpeedKmh > 0f -> prefs.noxWarnSimSpeedKmh
            prefs.noxWarnSim -> signals.speedKmh.coerceAtLeast(prefs.noxWarnSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = NoxWarnActive.evaluate(active, speed, prefs.noxWarnSpeedMinKmh)
        if (!prefs.noxWarnEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        if (nowMs - warnSinceMs >= 2000 && nowMs - lastSpokenMs >= 30000 && prefs.noxWarnTts) {
            lastSpokenMs = nowMs
            val phrase = NoxWarnActive.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, "nox_warn_alert", phrase, "critical", "nox_warn:${nowMs / 60000}", false)
        }
    }
}
