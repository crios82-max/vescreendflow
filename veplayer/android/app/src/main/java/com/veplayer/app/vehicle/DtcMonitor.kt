package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Announces MIL / new DTC codes via TTS + FleetInbox (cooldown).
 */
object DtcMonitor {
    data class State(
        val mil: Boolean = false,
        val count: Int = 0,
        val codes: List<String> = emptyList(),
        val label: String = "",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var lastMil = false
    private var lastCodesKey = ""
    private var lastSpokenMs = 0L
    private const val COOLDOWN_MS = 120_000L

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val mil = signals.mil
        val codes = signals.dtcs
        val codeStrs = codes.map { it.code }
        val label =
            when {
                mil && codeStrs.isNotEmpty() ->
                    "MIL · ${codeStrs.first()}" + if (codeStrs.size > 1) " +${codeStrs.size - 1}" else ""
                mil -> "MIL"
                codeStrs.isNotEmpty() ->
                    "DTC · ${codeStrs.first()}" + if (codeStrs.size > 1) " +${codeStrs.size - 1}" else ""
                else -> ""
            }
        _state.value = State(mil = mil, count = signals.dtcCount, codes = codeStrs, label = label)

        if (!prefs.dtcAlertsEnabled) return

        val key = codeStrs.sorted().joinToString(",")
        val milRose = mil && !lastMil
        val codesChanged = key != lastCodesKey && key.isNotEmpty()
        lastMil = mil
        if (key != lastCodesKey) lastCodesKey = key

        if (!milRose && !codesChanged) return
        val cooled = nowMs - lastSpokenMs >= COOLDOWN_MS
        if (!cooled) return
        lastSpokenMs = nowMs

        val phrase =
            when {
                milRose && codeStrs.isNotEmpty() ->
                    "Luz de motor encendida. Códigos ${codeStrs.take(3).joinToString(" ")}."
                milRose -> "Luz de motor encendida."
                else -> "Nuevo código de falla ${codeStrs.first()}."
            }
        if (prefs.dtcTts) NavTts.speakNow(phrase)
        FleetInbox.push(
            prefs = prefs,
            kind = if (milRose) "mil_on" else "dtc:${codeStrs.first()}",
            text = phrase,
            severity = "warn",
            id = "dtc:${if (mil) "mil" else key}:${nowMs / 120_000}",
            speak = false,
        )
    }
}
