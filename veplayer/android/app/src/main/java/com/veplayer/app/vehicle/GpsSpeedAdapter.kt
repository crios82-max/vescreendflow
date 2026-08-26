package com.veplayer.app.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Speed from fused location; gear/reverse only via explicit mock flags or future CAN merge.
 */
class GpsSpeedAdapter : VehicleSignalAdapter {
    private val _signals = MutableStateFlow(VehicleSignals(source = "gps"))
    override val signals: StateFlow<VehicleSignals> = _signals.asStateFlow()
    override val name: String = "gps"

    @Volatile private var running = false

    override fun start() {
        running = true
    }

    override fun stop() {
        running = false
    }

    fun ingest(
        speedMps: Float?,
        headingDeg: Float? = null,
        reverseOverride: Boolean = false,
    ) {
        if (!running) return
        _signals.update { cur ->
            val speed = (speedMps ?: cur.speedMps).coerceAtLeast(0f)
            val gear =
                when {
                    reverseOverride -> Gear.R
                    speed < 0.3f -> Gear.P
                    else -> Gear.D
                }
            cur.copy(
                speedMps = speed,
                gear = gear,
                headingDeg = headingDeg ?: cur.headingDeg,
                ignition = IgnitionState.ON,
                source = "gps",
                updatedAtMs = System.currentTimeMillis(),
            )
        }
    }
}
