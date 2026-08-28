package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AbsDisableMonitor {
    private val _state = MutableStateFlow(AbsDisable.State())
    val state: StateFlow<AbsDisable.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val supported = prefs.absDisableSim || signals.absDisableSupported == 1
        val disabled = when {
            prefs.absDisableSim -> true
            signals.absDisabled == 1 -> true
            else -> false
        }
        val speed = when {
            prefs.absDisableSim && prefs.absDisableSimSpeedKmh > 0f -> prefs.absDisableSimSpeedKmh
            prefs.absDisableSim -> signals.speedKmh.coerceAtLeast(prefs.absDisableSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = AbsDisable.evaluate(supported, disabled, speed, prefs.absDisableSpeedMinKmh)
        if (!prefs.absDisableEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        if (nowMs - warnSinceMs >= 2000 && nowMs - lastSpokenMs >= 30000 && prefs.absDisableTts) {
            lastSpokenMs = nowMs
            val phrase = AbsDisable.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, "abs_disable_alert", phrase, "critical", "abs_disable:alert:${nowMs / 60000}", false)
        }
    }
}
