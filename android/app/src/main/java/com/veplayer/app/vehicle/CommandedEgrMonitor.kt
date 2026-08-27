package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CommandedEgrMonitor {
    private val _state = MutableStateFlow(CommandedEgr.State())
    val state: StateFlow<CommandedEgr.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val pct = if (prefs.egrCmdSimPct > 0f) prefs.egrCmdSimPct else signals.egrCmdPct
        val speed = if (prefs.egrCmdSimPct > 0f) signals.speedKmh.coerceAtLeast(prefs.egrCmdSpeedMinKmh + 1f) else signals.speedKmh
        val st = CommandedEgr.evaluate(pct, speed, prefs.egrCmdWarnPct, prefs.egrCmdAlertPct, prefs.egrCmdSpeedMinKmh)
        if (!prefs.egrCmdEnabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.egrPct ?: 0f).toInt() / 5}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.egrCmdTts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = CommandedEgr.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "egr_cmd_alert" else "egr_cmd_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "egr_cmd:${st.band}:${nowMs / 60000}", false)
        }
    }
}
