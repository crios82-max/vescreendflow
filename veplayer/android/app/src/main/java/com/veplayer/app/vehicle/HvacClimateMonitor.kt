package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Climate panel state from live signals / local setpoints.
 */
object HvacClimateMonitor {
    private val _state = MutableStateFlow(HvacClimate.State())
    val state: StateFlow<HvacClimate.State> = _state.asStateFlow()

    fun tick(
        prefs: VePrefs,
        signals: VehicleSignals = VehicleState.state.value,
    ) {
        if (!prefs.hvacPanelEnabled) {
            _state.value = HvacClimate.State()
            return
        }
        HvacClimateBus.syncFrom(signals)
        val sp = HvacClimateBus.setpoint.value
        val cabin = if (sp.override) signals.hvacCabinC else signals.hvacCabinC
        val target = if (sp.override) sp.targetC else signals.hvacTargetC
        val ac = if (sp.override) sp.acOn else signals.hvacAcOn
        val fan = if (sp.override) sp.fanLevel else signals.hvacFanLevel
        _state.value =
            HvacClimate.evaluate(
                cabinC = cabin,
                targetC = target,
                acOn = ac,
                fanLevel = fan,
                comfortDeltaC = prefs.hvacComfortDeltaC,
            )
    }
}
