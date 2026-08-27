package com.veplayer.app.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Active geofence speed zone from SenseFlow heartbeat.
 */
object SpeedZoneBus {
    data class Zone(
        val id: Int,
        val name: String,
        val maxKmh: Int,
        val distanceM: Int = 0,
    )

    private val _zone = MutableStateFlow<Zone?>(null)
    val zone: StateFlow<Zone?> = _zone.asStateFlow()

    fun apply(zone: Zone?) {
        _zone.value = zone
    }

    fun clear() {
        _zone.value = null
    }
}
