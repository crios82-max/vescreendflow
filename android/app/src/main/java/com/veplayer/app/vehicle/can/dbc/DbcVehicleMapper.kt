package com.veplayer.app.vehicle.can.dbc

import com.veplayer.app.vehicle.Gear
import com.veplayer.app.vehicle.IgnitionState
import com.veplayer.app.vehicle.TurnSignal
import com.veplayer.app.vehicle.VehicleSignals
import com.veplayer.app.vehicle.can.CanFrame

/**
 * Maps DBC signal names → [VehicleSignals].
 * Aliases cover VePlayer demo DBC and common OEM names.
 */
object DbcVehicleMapper {
    fun apply(
        frame: CanFrame,
        db: DbcDatabase,
        base: VehicleSignals,
        sourceTag: String,
    ): VehicleSignals {
        val msg = db.message(frame.id) ?: return base
        var snap = base.copy(source = sourceTag, updatedAtMs = frame.timestampMs)
        for (sig in msg.signals) {
            val phys = DbcBitExtract.physical(frame.data, sig)
            snap = applySignal(snap, sig.name, phys, frame)
        }
        return snap
    }

    private fun applySignal(
        base: VehicleSignals,
        name: String,
        value: Double,
        frame: CanFrame,
    ): VehicleSignals {
        val key = normalize(name)
        val i = value.toInt()
        val f = value.toFloat()
        val on = value >= 0.5
        return when (key) {
            "speed_kmh", "speed", "vehiclespeed", "veh_speed", "speedkmh" ->
                base.copy(speedMps = f / 3.6f, updatedAtMs = frame.timestampMs)
            "speed_mps", "speedmps" ->
                base.copy(speedMps = f, updatedAtMs = frame.timestampMs)
            "gear", "prnd", "gearlever", "selectedgear" ->
                base.copy(
                    gear =
                        when (i) {
                            0 -> Gear.P
                            1 -> Gear.R
                            2 -> Gear.N
                            3 -> Gear.D
                            4 -> Gear.L
                            else -> Gear.UNKNOWN
                        },
                    updatedAtMs = frame.timestampMs,
                )
            "turn", "turnsignal", "turn_indicator", "blinker" ->
                base.copy(
                    turn =
                        when (i) {
                            1 -> TurnSignal.LEFT
                            2 -> TurnSignal.RIGHT
                            3 -> TurnSignal.HAZARD
                            else -> TurnSignal.OFF
                        },
                    updatedAtMs = frame.timestampMs,
                )
            "door_fl", "doorfl", "fl_door" -> base.copy(doorFl = on)
            "door_fr", "doorfr", "fr_door" -> base.copy(doorFr = on)
            "door_rl", "doorrl", "rl_door" -> base.copy(doorRl = on)
            "door_rr", "doorrr", "rr_door" -> base.copy(doorRr = on)
            "trunk", "trunk_open", "boot" -> base.copy(trunkOpen = on)
            "hood", "bonnet", "hood_open" -> base.copy(hoodOpen = on)
            "doors_mask", "door_mask", "doors" -> {
                val m = i
                base.copy(
                    doorFl = m and 0x01 != 0,
                    doorFr = m and 0x02 != 0,
                    doorRl = m and 0x04 != 0,
                    doorRr = m and 0x08 != 0,
                    trunkOpen = m and 0x10 != 0,
                    hoodOpen = m and 0x20 != 0,
                )
            }
            "soc", "battery_soc", "hv_soc", "batterysocpct", "soc_pct" ->
                base.copy(batterySocPct = f.coerceIn(0f, 100f), rangeKm = f * 3.2f)
            "fuel", "fuel_pct", "fuellevel" ->
                base.copy(fuelPct = f.coerceIn(0f, 100f))
            "steering", "steering_angle", "steer_angle", "steeringangledeg" ->
                base.copy(steeringAngleDeg = f)
            "rpm", "engine_rpm", "enginerpm" ->
                base.copy(rpm = f)
            "abs", "abs_active", "esc_active" ->
                base.copy(absActive = on)
            "parking", "parking_brake", "epb" ->
                base.copy(parkingBrake = on)
            "seatbelt", "seatbelt_driver", "driver_belt" ->
                // demo DBC: 1 = unbuckled bit style from flags; physical 0 = buckled
                base.copy(seatbeltDriver = !on)
            "ignition", "ign_state", "ignitionstate" ->
                base.copy(
                    ignition =
                        when (i) {
                            1 -> IgnitionState.ACC
                            2 -> IgnitionState.ON
                            3 -> IgnitionState.START
                            else -> IgnitionState.OFF
                        },
                )
            "tpms_fl", "tpmsfl", "fl_psi" -> base.copy(tpmsFlPsi = f)
            "tpms_fr", "tpmsfr", "fr_psi" -> base.copy(tpmsFrPsi = f)
            "tpms_rl", "tpmsrl", "rl_psi" -> base.copy(tpmsRlPsi = f)
            "tpms_rr", "tpmsrr", "rr_psi" -> base.copy(tpmsRrPsi = f)
            "hvac_cabin", "cabin_temp", "cabinc" -> base.copy(hvacCabinC = f)
            "hvac_target", "target_temp", "set_temp" -> base.copy(hvacTargetC = f)
            "hvac_fan", "fan_level", "blower" -> base.copy(hvacFanLevel = i.coerceIn(0, 7))
            "hvac_ac", "ac_on", "ac" -> base.copy(hvacAcOn = on)
            "outdoor", "outdoor_temp", "ambient" -> base.copy(outdoorTempC = f)
            "throttle", "throttle_pct", "accel_pedal" -> base.copy(throttlePct = f)
            "coolant", "coolant_c" -> base.copy(coolantC = f)
            "battery_v", "battery_voltage", "batt_v", "module_voltage", "voltage_v" ->
                base.copy(batteryVoltageV = f)
            "odometer", "odo_km" -> base.copy(odometerKm = f)
            "yaw", "yaw_rate" -> base.copy(yawRateDegS = f)
            else -> base
        }
    }

    private fun normalize(name: String): String =
        name.lowercase()
            .replace("-", "_")
            .replace(" ", "_")
            .replace("__", "_")
}
