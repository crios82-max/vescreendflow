package com.veplayer.app.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class VehicleSnapshot(
    val speedMps: Float = 0f,
    val reverse: Boolean = false,
    val source: String = "idle",
) {
    val speedKmh: Float get() = speedMps * 3.6f
}

/**
 * Shared vehicle motion state — GPS / mock / future CAN.
 */
object VehicleState {
    private val _state = MutableStateFlow(VehicleSnapshot())
    val state: StateFlow<VehicleSnapshot> = _state.asStateFlow()

    fun updateSpeed(speedMps: Float?, source: String = "gps") {
        if (speedMps == null) return
        _state.update { it.copy(speedMps = speedMps.coerceAtLeast(0f), source = source) }
    }

    fun setReverse(reverse: Boolean, source: String = "mock") {
        _state.update { it.copy(reverse = reverse, source = source) }
    }

    fun applyMock(speedKmh: Float, reverse: Boolean) {
        _state.update {
            it.copy(
                speedMps = (speedKmh / 3.6f).coerceAtLeast(0f),
                reverse = reverse,
                source = "mock",
            )
        }
    }

    fun shouldBlockVideo(thresholdKmh: Float): Boolean {
        val s = _state.value
        if (s.reverse) return true
        return s.speedKmh >= thresholdKmh
    }
}
