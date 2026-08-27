package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EquivRatioMonitor {
    private val _state = MutableStateFlow(EquivRatio.State())
    val state: StateFlow<EquivRatio.State> = _state.asStateFlow()
    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 2_000L
    private const val COOLDOWN_MS = 30_000L

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val ratio =
            when {
                prefs.equivSimRatio > 0f -> prefs.equivSimRatio
                else -> signals.equivRatio
            }
        val speed =
            when {
                prefs.equivSimRatio > 0f && prefs.equivSimSpeedKmh > 0f -> prefs.equivSimSpeedKmh
                prefs.equivSimRatio > 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.equivSpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val st =
            EquivRatio.evaluate(
                ratio = ratio,
                speedKmh = speed,
                rpm = signals.rpm,
                warnLow = prefs.equivWarnLow,
                alertLow = prefs.equivAlertLow,
                warnHigh = prefs.equivWarnHigh,
                alertHigh = prefs.equivAlertHigh,
                speedMinKmh = prefs.equivSpeedMinKmh,
                rpmMin = prefs.equivRpmMin,
            )
        if (!prefs.equivEnabled) {
            _state.value = st.copy(showWarn = false)
            return
        }
        _state.value = st
        if (!st.showWarn) {
            warnSinceMs = 0L
            lastKey = ""
            return
        }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val held = nowMs - warnSinceMs >= HOLD_MS
        val cooled = nowMs - lastSpokenMs >= COOLDOWN_MS
        val key = "${st.band}:${((st.ratio ?: 0f) * 100).toInt() / 5}"
        if (held && (cooled || key != lastKey) && prefs.equivTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = EquivRatio.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "equiv_alert" else "equiv_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "equiv:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
