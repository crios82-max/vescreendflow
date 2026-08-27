package com.veplayer.app.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Local climate setpoints for mock / obd_sim (and UI panel).
 * When [Setpoint.override] is true, adapters honour target/AC/fan.
 */
object HvacClimateBus {
    data class Setpoint(
        val targetC: Float = 22f,
        val acOn: Boolean = true,
        val fanLevel: Int = 2,
        val override: Boolean = false,
    )

    private val _setpoint = MutableStateFlow(Setpoint())
    val setpoint: StateFlow<Setpoint> = _setpoint.asStateFlow()

    /** Simulated cabin when override is active (drifts toward target). */
    private var simCabinC: Float? = null

    fun syncFrom(signals: VehicleSignals) {
        if (_setpoint.value.override) return
        val cabin = signals.hvacCabinC
        val target = signals.hvacTargetC ?: cabin ?: 22f
        _setpoint.value =
            Setpoint(
                targetC = target,
                acOn = signals.hvacAcOn,
                fanLevel = signals.hvacFanLevel.coerceIn(0, 7),
                override = false,
            )
        if (cabin != null) simCabinC = cabin
    }

    fun enableOverride(from: VehicleSignals? = null) {
        val cur = _setpoint.value
        val base = from ?: VehicleState.state.value
        _setpoint.value =
            Setpoint(
                targetC = cur.targetC.takeIf { cur.override } ?: base.hvacTargetC ?: 22f,
                acOn = if (cur.override) cur.acOn else base.hvacAcOn,
                fanLevel = if (cur.override) cur.fanLevel else base.hvacFanLevel.coerceIn(0, 7).coerceAtLeast(1),
                override = true,
            )
        if (simCabinC == null) simCabinC = base.hvacCabinC ?: _setpoint.value.targetC
    }

    fun clearOverride() {
        _setpoint.value = _setpoint.value.copy(override = false)
    }

    fun nudgeTarget(deltaC: Float) {
        enableOverride()
        val next = (_setpoint.value.targetC + deltaC).coerceIn(16f, 30f)
        _setpoint.value = _setpoint.value.copy(targetC = next, override = true)
    }

    fun setTarget(c: Float) {
        enableOverride()
        _setpoint.value = _setpoint.value.copy(targetC = c.coerceIn(16f, 30f), override = true)
    }

    fun toggleAc() {
        enableOverride()
        _setpoint.value = _setpoint.value.copy(acOn = !_setpoint.value.acOn, override = true)
    }

    fun setAc(on: Boolean) {
        enableOverride()
        _setpoint.value = _setpoint.value.copy(acOn = on, override = true)
    }

    fun cycleFan() {
        enableOverride()
        val next = (_setpoint.value.fanLevel % 5) + 1
        _setpoint.value = _setpoint.value.copy(fanLevel = next, override = true)
    }

    fun setFan(level: Int) {
        enableOverride()
        _setpoint.value = _setpoint.value.copy(fanLevel = level.coerceIn(0, 7), override = true)
    }

    /**
     * Cabin temp for mock adapters when override is on — eases toward target.
     */
    fun tickSimCabin(dtSec: Float = 0.25f): Float {
        val sp = _setpoint.value
        var cabin = simCabinC ?: (sp.targetC + 2f)
        val rate =
            when {
                !sp.acOn && sp.fanLevel == 0 -> 0.05f
                sp.acOn -> 0.35f + sp.fanLevel * 0.08f
                else -> 0.15f + sp.fanLevel * 0.05f
            }
        val step = (sp.targetC - cabin) * (rate * dtSec).coerceIn(0.01f, 0.5f)
        cabin += step
        if (abs(cabin - sp.targetC) < 0.05f) cabin = sp.targetC
        simCabinC = cabin
        return cabin
    }

    fun applyToSignals(base: VehicleSignals): VehicleSignals {
        val sp = _setpoint.value
        if (!sp.override) return base
        return base.copy(
            hvacCabinC = tickSimCabin(),
            hvacTargetC = sp.targetC,
            hvacAcOn = sp.acOn,
            hvacFanLevel = sp.fanLevel,
        )
    }
}
