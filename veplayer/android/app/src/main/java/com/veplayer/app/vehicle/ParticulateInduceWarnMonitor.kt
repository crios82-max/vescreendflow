package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ParticulateInduceWarnMonitor {
    private val _state = MutableStateFlow(ParticulateInduceWarn.State())
    val state: StateFlow<ParticulateInduceWarn.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val status = when {
            prefs.particulateInduceSimStatus > 0f -> prefs.particulateInduceSimStatus.toInt()
            else -> signals.particulateInduceStatus
        }
        val speed = when {
            prefs.particulateInduceSimStatus > 0f && prefs.particulateInduceSimSpeedKmh > 0f -> prefs.particulateInduceSimSpeedKmh
            prefs.particulateInduceSimStatus > 0f -> signals.speedKmh.coerceAtLeast(prefs.particulateInduceWarnSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = ParticulateInduceWarn.evaluate(status, speed, prefs.particulateInduceWarnStatus, prefs.particulateInduceWarnSpeedMinKmh)
        if (!prefs.particulateInduceWarnEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        if (nowMs - warnSinceMs >= 2000 && nowMs - lastSpokenMs >= 30000 && prefs.particulateInduceWarnTts) {
            lastSpokenMs = nowMs
            val phrase = ParticulateInduceWarn.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, "particulate_induce_warn", phrase, "warn", "particulate_induce:warn:${nowMs / 60000}", false)
        }
    }
}
