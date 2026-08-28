package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NoxInduceLevel2Monitor {
    private val _state = MutableStateFlow(NoxInduceLevel2.State())
    val state: StateFlow<NoxInduceLevel2.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val status = when {
            prefs.noxIndL2Sim > 0 -> prefs.noxIndL2Sim
            else -> signals.noxInduceLevel2
        }
        val speed = when {
            prefs.noxIndL2Sim > 0 && prefs.noxIndL2SimSpeedKmh > 0f -> prefs.noxIndL2SimSpeedKmh
            prefs.noxIndL2Sim > 0 -> signals.speedKmh.coerceAtLeast(prefs.noxIndL2SpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = NoxInduceLevel2.evaluate(status, speed, prefs.noxIndL2SpeedMinKmh)
        if (!prefs.noxIndL2Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${st.status ?: 0}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.noxIndL2Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = NoxInduceLevel2.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "nox_ind_l2_alert" else "nox_ind_l2_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "nox_ind_l2:${st.band}:${nowMs / 60000}", false)
        }
    }
}
