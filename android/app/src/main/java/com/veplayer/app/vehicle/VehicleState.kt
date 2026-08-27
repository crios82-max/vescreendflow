package com.veplayer.app.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** @deprecated Prefer [VehicleSignals]; kept as typealias for call sites. */
typealias VehicleSnapshot = VehicleSignals

/**
 * Shared vehicle motion + CAN telemetry.
 * Prefer [CanBusManager] as the writer; UI reads [state].
 */
object VehicleState {
    private val _state = MutableStateFlow(VehicleSignals())
    val state: StateFlow<VehicleSignals> = _state.asStateFlow()

    fun applySignals(signals: VehicleSignals) {
        val d = DtcBus.snap.value
        _state.value =
            signals.copy(
                mil = d.mil,
                dtcCount = if (d.dtcCount > 0) d.dtcCount else d.codes.size,
                dtcs = d.codes,
            )
    }

    fun updateSpeed(speedMps: Float?, source: String = "gps") {
        if (speedMps == null) return
        _state.update {
            it.copy(
                speedMps = speedMps.coerceAtLeast(0f),
                gear = if (it.gear == Gear.R) Gear.R else if (speedMps < 0.3f) Gear.P else Gear.D,
                source = source,
                updatedAtMs = System.currentTimeMillis(),
            )
        }
    }

    fun setReverse(reverse: Boolean, source: String = "mock") {
        _state.update {
            it.copy(
                gear = if (reverse) Gear.R else if (it.speedMps < 0.3f) Gear.P else Gear.D,
                source = source,
                updatedAtMs = System.currentTimeMillis(),
            )
        }
    }

    fun applyMock(speedKmh: Float, reverse: Boolean) {
        _state.update {
            it.copy(
                speedMps = (speedKmh / 3.6f).coerceAtLeast(0f),
                gear = if (reverse) Gear.R else if (speedKmh < 0.5f) Gear.P else Gear.D,
                parkingBrake = reverse.not() && speedKmh < 0.5f,
                source = "mock",
                updatedAtMs = System.currentTimeMillis(),
            )
        }
    }

    fun patchHeading(headingDeg: Float) {
        _state.update {
            it.copy(headingDeg = headingDeg, updatedAtMs = System.currentTimeMillis())
        }
    }

    fun shouldBlockVideo(thresholdKmh: Float): Boolean {
        val s = _state.value
        if (s.reverse) return true
        return s.speedKmh >= thresholdKmh
    }
}
