package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NoxInduceLevel1Monitor {
    private val _state = MutableStateFlow(NoxInduceLevel1.State())
    val state: StateFlow<NoxInduceLevel1.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val status = when {
            prefs.noxIndL1Sim > 0 -> prefs.noxIndL1Sim
            else -> signals.noxInduceLevel1
        }
        val speed = when {
            prefs.noxIndL1Sim > 0 && prefs.noxIndL1SimSpeedKmh > 0f -> prefs.noxIndL1SimSpeedKmh
            prefs.noxIndL1Sim > 0 -> signals.speedKmh.coerceAtLeast(prefs.noxIndL1SpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = NoxInduceLevel1.evaluate(status, speed, prefs.noxIndL1SpeedMinKmh)
        if (!prefs.noxIndL1Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.status ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.noxIndL1Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = NoxInduceLevel1.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "nox_ind_l1_alert" else "nox_ind_l1_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "nox_ind_l1:${st.band}:${nowMs / 60000}", false)
        }
    }
}
