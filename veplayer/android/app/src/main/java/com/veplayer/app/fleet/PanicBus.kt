package com.veplayer.app.fleet

import com.veplayer.app.data.VePrefs
import com.veplayer.app.nav.NavTts
import com.veplayer.app.vehicle.VehicleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Driver SOS / panic — long-press on DriveViz sends critical fleet alert.
 */
object PanicBus {
    data class State(
        val active: Boolean = false,
        val alertId: Long? = null,
        val message: String = "",
        val sentAtMs: Long = 0L,
        val holding: Boolean = false,
        val holdProgress: Float = 0f,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun setHolding(holding: Boolean, progress: Float = 0f) {
        _state.value = _state.value.copy(holding = holding, holdProgress = progress.coerceIn(0f, 1f))
    }

    fun applyFromHeartbeat(open: Boolean, alertId: Long?, message: String?) {
        if (open) {
            _state.value =
                _state.value.copy(
                    active = true,
                    alertId = alertId ?: _state.value.alertId,
                    message = message?.ifBlank { _state.value.message } ?: _state.value.message,
                )
        } else if (_state.value.active) {
            // Server cleared (ack) — keep quiet if already cleared locally
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
        lat: Double? = null,
        lng: Double? = null,
        note: String? = null,
    ): Result<State> =
        withContext(Dispatchers.IO) {
            if (!prefs.panicEnabled) return@withContext Result.failure(IllegalStateException("SOS desactivado"))
            val snap = VehicleState.state.value
            val result =
                fleet.panic(
                    lat = lat,
                    lng = lng,
                    note = note,
                    driverCode = prefs.driverCode.ifBlank { null },
                    driverName = prefs.driverName.ifBlank { null },
                )
            result.map { r ->
                val st =
                    State(
                        active = true,
                        alertId = r.alertId,
                        message = r.message,
                        sentAtMs = System.currentTimeMillis(),
                    )
                _state.value = st
                FleetInbox.push(
                    prefs = prefs,
                    kind = "panic",
                    text = r.message.ifBlank { "SOS enviado a flota" },
                    severity = "critical",
                    id = "panic:${r.alertId ?: System.currentTimeMillis()}",
                    speak = false,
                )
                NavTts.speakNow("SOS enviado a flota. Mantente en el vehículo.")
                st
            }
        }
}
