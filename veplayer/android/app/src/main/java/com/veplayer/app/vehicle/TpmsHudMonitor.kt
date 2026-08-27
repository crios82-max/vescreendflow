package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-wheel TPMS → TTS + inbox.
 */
object TpmsHudMonitor {
    private val _state = MutableStateFlow(TpmsHud.State())
    val state: StateFlow<TpmsHud.State> = _state.asStateFlow()

    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 2_000L
    private const val COOLDOWN_MS = 45_000L

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val sim = prefs.tpmsSimFlPsi
        val fl =
            when {
                sim > 0f -> sim
                else -> signals.tpmsFlPsi
            }
        val fr = signals.tpmsFrPsi
        val rl = signals.tpmsRlPsi
        val rr = signals.tpmsRrPsi
        val st =
            TpmsHud.evaluate(
                fl = fl,
                fr = fr,
                rl = rl,
                rr = rr,
                warnPsi = prefs.tpmsWarnPsi,
                alertPsi = prefs.tpmsAlertPsi,
            )
        if (!prefs.tpmsHudEnabled) {
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
        val key = "${st.band}:${st.lowWheels.joinToString(",")}"
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.tpmsTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = TpmsHud.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "tpms_alert" else "tpms_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "tpms:${st.band}:${nowMs / 120_000}",
                speak = false,
            )
        }
    }
}
