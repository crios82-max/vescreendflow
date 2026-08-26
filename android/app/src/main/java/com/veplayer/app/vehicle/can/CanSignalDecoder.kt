package com.veplayer.app.vehicle.can

import com.veplayer.app.vehicle.Gear
import com.veplayer.app.vehicle.IgnitionState
import com.veplayer.app.vehicle.TurnSignal
import com.veplayer.app.vehicle.VehicleSignals

/**
 * VePlayer demo DBC-lite (IDs 0x100–0x108).
 * Swap [decode] / map for OEM DBC without changing transports.
 *
 * | ID   | Payload |
 * |------|---------|
 * | 0x100 | speed km/h (u8) |
 * | 0x101 | gear 0=P 1=R 2=N 3=D 4=L |
 * | 0x102 | turn 0=off 1=L 2=R 3=hazard |
 * | 0x103 | doors bitmask FL FR RL RR trunk hood |
 * | 0x104 | SOC % · fuel % |
 * | 0x105 | steering int16 LE ×0.1° · RPM u16 LE |
 * | 0x106 | flags: abs · parking · seatbelt · ignition |
 * | 0x107 | TPMS psi FL FR RL RR |
 * | 0x108 | HVAC cabin · target · fan · ac bit |
 */
object CanSignalDecoder {
    const val ID_SPEED = 0x100
    const val ID_GEAR = 0x101
    const val ID_TURN = 0x102
    const val ID_DOORS = 0x103
    const val ID_ENERGY = 0x104
    const val ID_DYNAMICS = 0x105
    const val ID_FLAGS = 0x106
    const val ID_TPMS = 0x107
    const val ID_HVAC = 0x108

    fun apply(
        frame: CanFrame,
        base: VehicleSignals,
        sourceTag: String,
    ): VehicleSignals {
        val d = frame.data
        return when (frame.id and 0x7FF) {
            ID_SPEED -> {
                val kmh = u8(d, 0).toFloat()
                base.copy(
                    speedMps = kmh / 3.6f,
                    source = sourceTag,
                    updatedAtMs = frame.timestampMs,
                )
            }
            ID_GEAR -> {
                val gear =
                    when (u8(d, 0)) {
                        0 -> Gear.P
                        1 -> Gear.R
                        2 -> Gear.N
                        3 -> Gear.D
                        4 -> Gear.L
                        else -> Gear.UNKNOWN
                    }
                base.copy(gear = gear, source = sourceTag, updatedAtMs = frame.timestampMs)
            }
            ID_TURN -> {
                val turn =
                    when (u8(d, 0)) {
                        1 -> TurnSignal.LEFT
                        2 -> TurnSignal.RIGHT
                        3 -> TurnSignal.HAZARD
                        else -> TurnSignal.OFF
                    }
                base.copy(turn = turn, source = sourceTag, updatedAtMs = frame.timestampMs)
            }
            ID_DOORS -> {
                val m = u8(d, 0)
                base.copy(
                    doorFl = m and 0x01 != 0,
                    doorFr = m and 0x02 != 0,
                    doorRl = m and 0x04 != 0,
                    doorRr = m and 0x08 != 0,
                    trunkOpen = m and 0x10 != 0,
                    hoodOpen = m and 0x20 != 0,
                    source = sourceTag,
                    updatedAtMs = frame.timestampMs,
                )
            }
            ID_ENERGY ->
                base.copy(
                    batterySocPct = u8(d, 0).toFloat().takeIf { it in 0..100 },
                    fuelPct = u8(d, 1).toFloat().takeIf { it in 1..100 },
                    rangeKm = u8(d, 0) * 3.2f,
                    source = sourceTag,
                    updatedAtMs = frame.timestampMs,
                )
            ID_DYNAMICS -> {
                val steer = i16le(d, 0) / 10f
                val rpm = u16le(d, 2).toFloat()
                base.copy(
                    steeringAngleDeg = steer,
                    rpm = rpm,
                    yawRateDegS = steer * 0.12f,
                    source = sourceTag,
                    updatedAtMs = frame.timestampMs,
                )
            }
            ID_FLAGS -> {
                val f = u8(d, 0)
                val ign =
                    when (u8(d, 1)) {
                        1 -> IgnitionState.ACC
                        2 -> IgnitionState.ON
                        3 -> IgnitionState.START
                        else -> IgnitionState.OFF
                    }
                base.copy(
                    absActive = f and 0x01 != 0,
                    parkingBrake = f and 0x02 != 0,
                    seatbeltDriver = f and 0x04 == 0,
                    ignition = ign,
                    source = sourceTag,
                    updatedAtMs = frame.timestampMs,
                )
            }
            ID_TPMS ->
                base.copy(
                    tpmsFlPsi = u8(d, 0).toFloat(),
                    tpmsFrPsi = u8(d, 1).toFloat(),
                    tpmsRlPsi = u8(d, 2).toFloat(),
                    tpmsRrPsi = u8(d, 3).toFloat(),
                    source = sourceTag,
                    updatedAtMs = frame.timestampMs,
                )
            ID_HVAC ->
                base.copy(
                    hvacCabinC = u8(d, 0).toFloat(),
                    hvacTargetC = u8(d, 1).toFloat(),
                    hvacFanLevel = u8(d, 2).coerceIn(0, 7),
                    hvacAcOn = u8(d, 3) and 0x01 != 0,
                    outdoorTempC = u8(d, 4).takeIf { d.size > 4 }?.toFloat() ?: base.outdoorTempC,
                    source = sourceTag,
                    updatedAtMs = frame.timestampMs,
                )
            else -> base
        }
    }

    private fun u8(
        d: ByteArray,
        i: Int,
    ): Int = if (i < d.size) d[i].toInt() and 0xFF else 0

    private fun u16le(
        d: ByteArray,
        i: Int,
    ): Int = u8(d, i) or (u8(d, i + 1) shl 8)

    private fun i16le(
        d: ByteArray,
        i: Int,
    ): Int {
        val u = u16le(d, i)
        return if (u >= 0x8000) u - 0x10000 else u
    }
}
