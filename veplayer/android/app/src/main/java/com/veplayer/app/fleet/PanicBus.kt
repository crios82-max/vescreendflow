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
 * Driver SOS / panic — long-press on DriveViz sends critical fleet alert + dashcam clip.
 */
object PanicBus {
    data class State(
        val active: Boolean = false,
        val alertId: Long? = null,
        val message: String = "",
        val sentAtMs: Long = 0L,
        val holding: Boolean = false,
        val holdProgress: Float = 0f,
        val clipUrl: String? = null,
        val clipBytes: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun setHolding(holding: Boolean, progress: Float = 0f) {
        _state.value = _state.value.copy(holding = holding, holdProgress = progress.coerceIn(0f, 1f))
    }

    fun applyFromHeartbeat(open: Boolean, alertId: Long?, message: String?, clipUrl: String? = null) {
        if (open) {
            _state.value =
                _state.value.copy(
                    active = true,
                    alertId = alertId ?: _state.value.alertId,
                    message = message?.ifBlank { _state.value.message } ?: _state.value.message,
                    clipUrl = clipUrl?.takeIf { it.isNotBlank() } ?: _state.value.clipUrl,
                )
        } else if (_state.value.active) {
            clear(speak = false)
        }
    }

    fun clear(speak: Boolean = true) {
        val was = _state.value.active
        _state.value = State()
        if (was && speak) {
            NavTts.speakNow("SOS cancelado. Flota confirmó recepción.")
        }
    }

    suspend fun trigger(
        prefs: VePrefs,
        fleet: FleetClient,
        context: Context? = null,
        lat: Double? = null,
        lng: Double? = null,
        note: String? = null,
    ): Result<State> =
        withContext(Dispatchers.IO) {
            if (!prefs.panicEnabled) return@withContext Result.failure(IllegalStateException("SOS desactivado"))
            val result =
                fleet.panic(
                    lat = lat,
                    lng = lng,
                    note = note,
                    driverCode = prefs.driverCode.ifBlank { null },
                    driverName = prefs.driverName.ifBlank { null },
                )
            result.map { r ->
                var clipUrl: String? = r.clipUrl
                var clipBytes = 0
                if (prefs.sosClipEnabled && context != null) {
                    val clip =
                        SosDashcam.captureAndUpload(
                            context = context.applicationContext,
                            prefs = prefs,
                            fleet = fleet,
                            alertId = r.alertId,
                        )
                    clipUrl = clip.clipUrl ?: clipUrl
                    clipBytes = clip.bytes
                }
                val st =
                    State(
                        active = true,
                        alertId = r.alertId,
                        message = r.message,
                        sentAtMs = System.currentTimeMillis(),
                        clipUrl = clipUrl,
                        clipBytes = clipBytes,
                    )
                _state.value = st
                FleetInbox.push(
                    prefs = prefs,
                    kind = "panic",
                    text =
                        buildString {
                            append(r.message.ifBlank { "SOS enviado a flota" })
                            if (!clipUrl.isNullOrBlank()) append(" · clip OK")
                        },
                    severity = "critical",
                    id = "panic:${r.alertId ?: System.currentTimeMillis()}",
                    speak = false,
                )
                NavTts.speakNow(
                    if (!clipUrl.isNullOrBlank()) {
                        "SOS enviado a flota con clip de cámara. Mantente en el vehículo."
                    } else {
                        "SOS enviado a flota. Mantente en el vehículo."
                    },
                )
                st
            }
        }
}
