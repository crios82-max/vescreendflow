package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stuck O2 voltage (OBD 014A) → TTS + inbox. */
object O2VoltageMonitor {
    private val _state = MutableStateFlow(O2Voltage.State())
    val state: StateFlow<O2Voltage.State> = _state.asStateFlow()

    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 2_000L
    private const val COOLDOWN_MS = 30_000L

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val volts =
            when {
                prefs.o2SimVolts > 0f -> prefs.o2SimVolts
                else -> signals.o2B1s1Volts
            }
        val speed =
            when {
                prefs.o2SimVolts > 0f && prefs.o2SimSpeedKmh > 0f -> prefs.o2SimSpeedKmh
                prefs.o2SimVolts > 0f ->
                    signals.speedKmh.coerceAtLeast(prefs.o2SpeedMinKmh + 1f)
                else -> signals.speedKmh
            }
        val rpm = signals.rpm
        val st =
            O2Voltage.evaluate(
                o2Volts = volts,
                speedKmh = speed,
                rpm = rpm,
                warnLowV = prefs.o2WarnLowV,
                alertLowV = prefs.o2AlertLowV,
                warnHighV = prefs.o2WarnHighV,
                alertHighV = prefs.o2AlertHighV,
                speedMinKmh = prefs.o2SpeedMinKmh,
                rpmMin = prefs.o2RpmMin,
            )
        if (!prefs.o2Enabled) {
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
        val key = "${st.band}:${((st.o2Volts ?: 0f) * 100).toInt() / 5}"
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.o2Tts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = O2Voltage.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "o2_alert" else "o2_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "o2:${st.band}:${nowMs / 60_000}",
                speak = false,
            )
        }
    }
}
