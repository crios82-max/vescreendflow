package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sudden fuel drop → TTS + inbox. Tracks peak fuel in a sliding window.
 */
object SuddenFuelDropMonitor {
    private val _state = MutableStateFlow(SuddenFuelDrop.State())
    val state: StateFlow<SuddenFuelDrop.State> = _state.asStateFlow()

    private data class Sample(val atMs: Long, val pct: Float)

    private val samples = ArrayDeque<Sample>()
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private var warnSinceMs = 0L
    private const val HOLD_MS = 1_500L
    private const val COOLDOWN_MS = 45_000L
    /** Ignore tiny sensor noise when updating peak. */
    private const val NOISE_PCT = 0.5f

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val windowMs = (prefs.fuelDropWindowSec * 1000f).toLong().coerceIn(5_000L, 600_000L)
        val liveFuel = signals.fuelPct
        val simDrop = prefs.fuelDropSimDropPct
        val fuel =
            when {
                simDrop > 0f -> (100f - simDrop).coerceIn(0f, 100f)
                else -> liveFuel
            }
        val dropPct =
            when {
                simDrop > 0f -> simDrop
                fuel == null -> 0f
                else -> {
                    samples.addLast(Sample(nowMs, fuel))
                    while (samples.isNotEmpty() && nowMs - samples.first().atMs > windowMs) {
                        samples.removeFirst()
                    }
                    // Refuel: reset window so upward jumps don't poison peak.
                    val peak = samples.maxOfOrNull { it.pct } ?: fuel
                    if (fuel > peak + NOISE_PCT) {
                        samples.clear()
                        samples.addLast(Sample(nowMs, fuel))
                        0f
                    } else {
                        (peak - fuel).coerceAtLeast(0f)
                    }
                }
            }
        val st =
            SuddenFuelDrop.evaluate(
                fuelPct = fuel,
                dropPct = dropPct,
                warnPct = prefs.fuelDropWarnPct,
                alertPct = prefs.fuelDropAlertPct,
                windowSec = prefs.fuelDropWindowSec,
            )
        if (!prefs.fuelDropEnabled) {
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
        val key = "${st.band}:${st.dropPct.toInt()}"
        val changed = key != lastKey
        if (held && (cooled || changed) && prefs.fuelDropTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = SuddenFuelDrop.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "fuel_drop_alert" else "fuel_drop_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "fuel_drop:${st.band}:${nowMs / 120_000}",
                speak = false,
            )
        }
    }
}
