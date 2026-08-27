package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Long engine runtime (OBD 011F) → TTS + inbox.
 */
object EngineRuntimeMonitor {
    private val _state = MutableStateFlow(EngineRuntime.State())
    val state: StateFlow<EngineRuntime.State> = _state.asStateFlow()

    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val HOLD_MS = 3_000L
    private const val COOLDOWN_MS = 10 * 60_000L

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val runtime =
            when {
                prefs.engineRuntimeSimHours > 0f ->
                    (prefs.engineRuntimeSimHours * 3600f).toInt()
                else -> signals.runtimeSec
            }
        val st =
            EngineRuntime.evaluate(
                runtimeSec = runtime,
                warnSec = prefs.engineRuntimeWarnHours * 3600f,
                alertSec = prefs.engineRuntimeAlertHours * 3600f,
            )
        if (!prefs.engineRuntimeEnabled) {
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
        val key = "${st.band}:${(st.runtimeSec ?: 0) / 600}"
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.engineRuntimeTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = EngineRuntime.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "runtime_alert" else "runtime_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "runtime:${st.band}:${nowMs / 600_000}",
                speak = false,
            )
        }
    }
}
