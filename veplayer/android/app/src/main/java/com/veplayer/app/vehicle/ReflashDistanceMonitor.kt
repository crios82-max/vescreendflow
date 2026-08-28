package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ReflashDistanceMonitor {
    private val _state = MutableStateFlow(ReflashDistance.State())
    val state: StateFlow<ReflashDistance.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val km = when {
            prefs.reflashDistSimKm > 0f -> prefs.reflashDistSimKm
            else -> signals.reflashDistKm
        }
        val speed = when {
            prefs.reflashDistSimKm > 0f && prefs.reflashDistSimSpeedKmh > 0f -> prefs.reflashDistSimSpeedKmh
            prefs.reflashDistSimKm > 0f -> signals.speedKmh.coerceAtLeast(prefs.reflashDistSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = ReflashDistance.evaluate(km, speed, prefs.reflashDistWarnKm, prefs.reflashDistAlertKm, prefs.reflashDistSpeedMinKmh)
        if (!prefs.reflashDistEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${((st.distanceKm ?: 0f) / 500).toInt()}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.reflashDistTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = ReflashDistance.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "reflash_dist_alert" else "reflash_dist_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "reflash_dist:${st.band}:${nowMs / 60000}", false)
        }
    }
}
