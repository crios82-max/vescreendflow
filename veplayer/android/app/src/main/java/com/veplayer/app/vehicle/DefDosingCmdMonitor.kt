package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DefDosingCmdMonitor {
    private val _state = MutableStateFlow(DefDosingCmd.State())
    val state: StateFlow<DefDosingCmd.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val p = when {
            prefs.defDoseSimPct > 0f -> prefs.defDoseSimPct
            else -> signals.defDosingCmdPct
        }
        val speed = when {
            prefs.defDoseSimPct > 0f && prefs.defDoseSimSpeedKmh > 0f -> prefs.defDoseSimSpeedKmh
            prefs.defDoseSimPct > 0f -> signals.speedKmh.coerceAtLeast(prefs.defDoseSpeedMinKmh + 1f)
            else -> signals.speedKmh
        }
        val st = DefDosingCmd.evaluate(p, speed, prefs.defDoseWarnPct, prefs.defDoseAlertPct, prefs.defDoseSpeedMinKmh)
        if (!prefs.defDoseEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.dosePct ?: 0f).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.defDoseTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = DefDosingCmd.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "def_dose_alert" else "def_dose_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "def_dose:${st.band}:${nowMs / 60000}", false)
        }
    }
}
