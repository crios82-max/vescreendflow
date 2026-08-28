package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ParticulateInduceAlertMonitor {
    private val _state = MutableStateFlow(ParticulateInduceAlert.State())
    val state: StateFlow<ParticulateInduceAlert.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val status = when {
            prefs.particulateInduceSimStatus > 0f -> prefs.particulateInduceSimStatus.toInt()
            else -> signals.particulateInduceStatus
        }
        val speed = when {
            prefs.particulateInduceSimStatus > 0f && prefs.particulateInduceSimSpeedKmh > 0f -> prefs.particulateInduceSimSpeedKmh
            prefs.particulateInduceSimStatus > 0f -> signals.speedKmh.coerceAtLeast(prefs.particulateInduceAlertSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = ParticulateInduceAlert.evaluate(status, speed, prefs.particulateInduceAlertStatus, prefs.particulateInduceAlertSpeedMinKmh)
        if (!prefs.particulateInduceAlertEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        if (nowMs - warnSinceMs >= 2000 && nowMs - lastSpokenMs >= 30000 && prefs.particulateInduceAlertTts) {
            lastSpokenMs = nowMs
            val phrase = ParticulateInduceAlert.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, "particulate_induce_alert", phrase, "critical", "particulate_induce:alert:${nowMs / 60000}", false)
        }
    }
}
