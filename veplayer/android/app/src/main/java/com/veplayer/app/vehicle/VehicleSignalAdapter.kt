package com.veplayer.app.vehicle

import kotlinx.coroutines.flow.StateFlow

/**
 * Pluggable vehicle telemetry source.
 * Real head-units swap [MockCanAdapter] / [ObdElm327Adapter] for OEM SDK or SocketCAN.
 */
interface VehicleSignalAdapter {
    val name: String
    val signals: StateFlow<VehicleSignals>
    fun start()
    fun stop()
}
