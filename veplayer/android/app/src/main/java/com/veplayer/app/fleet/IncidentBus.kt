package com.veplayer.app.fleet

import android.content.Context
import com.veplayer.app.camera.SosDashcam
import com.veplayer.app.data.VePrefs
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Driver incident report (non-SOS) → fleet alert + optional dashcam frame.
 */
object IncidentBus {
    data class State(
        val lastAlertId: Long? = null,
        val lastMessage: String = "",
        val lastCategory: String = "",
        val lastClipUrl: String? = null,
        val sentAtMs: Long = 0L,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val categories =
        listOf(
            "accident" to "Accidente",
            "breakdown" to "Avería",
            "traffic" to "Tráfico",
            "other" to "Otro",
        )

    suspend fun report(
        prefs: VePrefs,
        fleet: FleetClient,
        context: Context?,
        category: String = "other",
        note: String? = null,
        withClip: Boolean = true,
    ): Result<State> =
        withContext(Dispatchers.IO) {
            if (!prefs.incidentEnabled) {
                return@withContext Result.failure(IllegalStateException("Incidentes desactivados"))
            }
            val jpeg =
                if (withClip && prefs.incidentClipEnabled && context != null) {
                    SosDashcam.renderSimFrame(prefs)
                } else {
                    null
                }
            val result =
                fleet.incident(
                    category = category,
                    note = note,
                    clipJpeg = jpeg,
                    clipSim = true,
                    driverCode = prefs.driverCode.ifBlank { null },
                    driverName = prefs.driverName.ifBlank { null },
                )
            result.map { r ->
                val st =
                    State(
                        lastAlertId = r.alertId,
                        lastMessage = r.message,
                        lastCategory = r.category,
                        lastClipUrl = r.clipUrl,
                        sentAtMs = System.currentTimeMillis(),
                    )
                _state.value = st
                FleetInbox.push(
                    prefs = prefs,
                    kind = "incident",
                    text = r.message,
                    severity = if (category == "accident") "critical" else "warn",
                    id = "incident:${r.alertId ?: System.currentTimeMillis()}",
                    speak = false,
                )
                NavTts.speakNow(
                    if (!r.clipUrl.isNullOrBlank()) {
                        "Incidente enviado a flota con clip."
                    } else {
                        "Incidente enviado a flota."
                    },
                )
                st
            }
        }
}
