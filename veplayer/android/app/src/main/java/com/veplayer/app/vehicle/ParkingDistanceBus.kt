package com.veplayer.app.vehicle

import kotlin.math.sin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live / simulated rear ultrasonic zones for parking HUD.
 */
object ParkingDistanceBus {
    private val _zones = MutableStateFlow(ParkingDistance.Zones())
    val zones: StateFlow<ParkingDistance.Zones> = _zones.asStateFlow()

    private var t = 0.0

    fun apply(z: ParkingDistance.Zones) {
        _zones.value = z
    }

    fun clear() {
        _zones.value = ParkingDistance.Zones()
        t = 0.0
    }

    /**
     * Demo sim when in reverse and no live USS: slowly approaches then eases.
     */
    fun tickSim(
        reverse: Boolean,
        enabled: Boolean,
        steeringDeg: Float? = null,
    ) {
        if (!enabled || !reverse) {
            if (!reverse) clear()
            return
        }
        t += 0.35
        val approach = (2.8 + 1.6 * sin(t / 7.0)).toFloat().coerceIn(0.35f, 4.5f)
        val steer = (steeringDeg ?: 0f) / 45f
        val left = (approach + 0.35f * steer + 0.2f * sin(t / 5.0).toFloat()).coerceIn(0.3f, 5f)
        val right = (approach - 0.35f * steer + 0.15f * sin(t / 4.5).toFloat()).coerceIn(0.3f, 5f)
        val center = (approach * 0.92f).coerceIn(0.25f, 5f)
        _zones.value = ParkingDistance.Zones(rearL = left, rearC = center, rearR = right)
    }
}
