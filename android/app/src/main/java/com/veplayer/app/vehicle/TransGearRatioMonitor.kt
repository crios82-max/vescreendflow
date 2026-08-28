package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TransGearRatioMonitor {
    private val _state = MutableStateFlow(TransGearRatio.State())
    val state: StateFlow<TransGearRatio.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val ratio = when {
            prefs.transGearSimRatio > 0f -> prefs.transGearSimRatio
            else -> signals.transGearRatio
        }
        val speed = when {
            prefs.transGearSimRatio > 0f && prefs.transGearSimSpeedKmh > 0f -> prefs.transGearSimSpeedKmh
            prefs.transGearSimRatio > 0f -> signals.speedKmh.coerceAtLeast(prefs.transGearSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = TransGearRatio.evaluate(ratio, speed, prefs.transGearWarnRatio, prefs.transGearAlertRatio, prefs.transGearSpeedMinKmh)
        if (!prefs.transGearEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${((st.gearRatio ?: 0f) * 10).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.transGearTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = TransGearRatio.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "trans_gear_alert" else "trans_gear_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "trans_gear:${st.band}:${nowMs / 60000}", false)
        }
    }
}
