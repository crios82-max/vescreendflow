package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NoxPcdLampMonitor {
    private val _state = MutableStateFlow(NoxPcdLamp.State())
    val state: StateFlow<NoxPcdLamp.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val lampOn = when {
            prefs.noxPcdLampSim -> true
            signals.noxPcdLampOn == 1 -> true
            else -> false
        }
        val speed = when {
            prefs.noxPcdLampSim && prefs.noxPcdLampSimSpeedKmh > 0f -> prefs.noxPcdLampSimSpeedKmh
            prefs.noxPcdLampSim -> signals.speedKmh.coerceAtLeast(prefs.noxPcdLampSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = NoxPcdLamp.evaluate(lampOn, speed, prefs.noxPcdLampSpeedMinKmh)
        if (!prefs.noxPcdLampEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        if (nowMs - warnSinceMs >= 2000 && nowMs - lastSpokenMs >= 30000 && prefs.noxPcdLampTts) {
            lastSpokenMs = nowMs
            val phrase = NoxPcdLamp.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, "nox_pcd_lamp_alert", phrase, "critical", "nox_pcd_lamp:alert:${nowMs / 60000}", false)
        }
    }
}
