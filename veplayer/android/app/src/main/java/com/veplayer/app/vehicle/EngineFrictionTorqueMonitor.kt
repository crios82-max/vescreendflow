package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EngineFrictionTorqueMonitor {
    private val _state = MutableStateFlow(EngineFrictionTorque.State())
    val state: StateFlow<EngineFrictionTorque.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val f = when {
            prefs.engFrictionSimPct != 0f -> prefs.engFrictionSimPct
            else -> signals.engineFrictionPct
        }
        val speed = when {
            prefs.engFrictionSimPct != 0f && prefs.engFrictionSimSpeedKmh > 0f -> prefs.engFrictionSimSpeedKmh
            prefs.engFrictionSimPct != 0f -> signals.speedKmh.coerceAtLeast(prefs.engFrictionSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = EngineFrictionTorque.evaluate(f, speed, prefs.engFrictionWarnPct, prefs.engFrictionAlertPct, prefs.engFrictionSpeedMinKmh)
        if (!prefs.engFrictionEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.frictionPct ?: 0f).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.engFrictionTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = EngineFrictionTorque.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "eng_friction_alert" else "eng_friction_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "eng_friction:${st.band}:${nowMs / 60000}", false)
        }
    }
}
