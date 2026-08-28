package com.veplayer.app.vehicle

/** Gear reported by CAN / OBD / mock. */
enum class Gear {
    P, R, N, D, L, UNKNOWN;

    companion object {
        fun from(raw: String?): Gear =
            when (raw?.uppercase()) {
                "P", "PARK" -> P
                "R", "REVERSE" -> R
                "N", "NEUTRAL" -> N
                "D", "DRIVE" -> D
                "L", "LOW", "1", "2", "3" -> L
                else -> UNKNOWN
            }
    }
}

enum class TurnSignal {
    OFF, LEFT, RIGHT, HAZARD;

    companion object {
        fun from(raw: String?): TurnSignal =
            when (raw?.lowercase()) {
                "left", "l" -> LEFT
                "right", "r" -> RIGHT
                "hazard", "hazards", "both" -> HAZARD
                else -> OFF
            }
    }
}

enum class IgnitionState {
    OFF, ACC, ON, START;

    companion object {
        fun from(raw: String?): IgnitionState =
            when (raw?.lowercase()) {
                "acc" -> ACC
                "on", "run" -> ON
                "start", "crank" -> START
                else -> OFF
            }
    }
}

/** Full vehicle telemetry snapshot (CAN / OBD / GPS / mock). */
data class VehicleSignals(
    val speedMps: Float = 0f,
    val gear: Gear = Gear.UNKNOWN,
    val turn: TurnSignal = TurnSignal.OFF,
    val doorFl: Boolean = false,
    val doorFr: Boolean = false,
    val doorRl: Boolean = false,
    val doorRr: Boolean = false,
    val trunkOpen: Boolean = false,
    val hoodOpen: Boolean = false,
    val parkingBrake: Boolean = false,
    val seatbeltDriver: Boolean = true,
    val batterySocPct: Float? = null,
    val fuelPct: Float? = null,
    /** Engine fuel rate (OBD PID 015E), grams/sec. */
    val fuelRateGps: Float? = null,
    val rangeKm: Float? = null,
    val rpm: Float? = null,
    val steeringAngleDeg: Float? = null,
    val coolantC: Float? = null,
    /** Engine oil temperature (OBD PID 015C). */
    val oilTempC: Float? = null,
    /** Intake air temperature (OBD PID 010F). */
    val intakeAirC: Float? = null,
    /** 12V system / control module voltage (OBD PID 0142). */
    val batteryVoltageV: Float? = null,
    val outdoorTempC: Float? = null,
    val ignition: IgnitionState = IgnitionState.ON,
    val headingDeg: Float? = null,
    val yawRateDegS: Float? = null,
    val odometerKm: Float? = null,
    /** ABS / ESC intervention active. */
    val absActive: Boolean = false,
    /** Tire pressure PSI (TPMS). */
    val tpmsFlPsi: Float? = null,
    val tpmsFrPsi: Float? = null,
    val tpmsRlPsi: Float? = null,
    val tpmsRrPsi: Float? = null,
    /** Cabin HVAC. */
    val hvacCabinC: Float? = null,
    val hvacTargetC: Float? = null,
    val hvacAcOn: Boolean = false,
    val hvacFanLevel: Int = 0,
        val throttlePct: Float? = null,
        val engineLoadPct: Float? = null,
        /** Short-term fuel trim % (OBD PID 0106), signed. */
        val fuelTrimStftPct: Float? = null,
        /** Long-term fuel trim % (OBD PID 0107), signed. */
        val fuelTrimLtftPct: Float? = null,
        /** Intake manifold absolute pressure (OBD PID 010B), kPa. */
        val mapKpa: Float? = null,
        /** Catalyst temperature (OBD PID 0134), °C. */
        val catalystTempC: Float? = null,
        /** Mass air flow (OBD PID 0110), grams/sec. */
        val mafGps: Float? = null,
        /** Fuel rail pressure (OBD PID 010A), kPa. */
        val fuelPressureKpa: Float? = null,
        /** Barometric pressure (OBD PID 0133), kPa. */
        val baroKpa: Float? = null,
        /** Ignition timing advance (OBD PID 010E), degrees. */
        val timingAdvanceDeg: Float? = null,
        /** O2 sensor voltage B1S1 (OBD PID 014A), volts. */
        val o2B1s1Volts: Float? = null,
        /** Absolute engine load % (OBD PID 0143). */
        val absoluteLoadPct: Float? = null,
        /** Relative throttle % (OBD PID 0145). */
        val relativeThrottlePct: Float? = null,
        /** Accelerator pedal D % (OBD PID 0149). */
        val accelPedalPct: Float? = null,
        /** O2 sensor voltage B1S2 (OBD PID 014B), volts. */
        val o2B1s2Volts: Float? = null,
        /** EGR error % (OBD PID 014D), signed. */
        val egrErrorPct: Float? = null,
        /** Commanded equivalence ratio (OBD PID 0144). */
        val equivRatio: Float? = null,
        /** Evap purge flow % (OBD PID 014E). */
        val evapPurgePct: Float? = null,
        /** Ethanol fuel % (OBD PID 0152). */
        val ethanolPct: Float? = null,
        /** Evap vapor pressure Pa (OBD PID 0153). */
        val evapVaporPa: Float? = null,
        /** Fuel rail absolute pressure kPa (OBD PID 0159). */
        val fuelRailAbsKpa: Float? = null,
        /** Commanded EGR % (OBD PID 014C). */
        val egrCmdPct: Float? = null,
        /** Relative accelerator pedal % (OBD PID 015A). */
        val relAccelPedalPct: Float? = null,
        /** Driver demand torque % (OBD PID 0161), signed. */
        val driverTorquePct: Float? = null,
        /** Actual engine torque % (OBD PID 0162), signed. */
        val actualTorquePct: Float? = null,
        /** Catalyst temperature bank 2 °C (OBD PID 0170). */
        val catalystB2TempC: Float? = null,
        /** Catalyst temp bank 1 sensor 2 °C (OBD PID 0171). */
        val catalystB1s2TempC: Float? = null,
        /** Catalyst temp bank 2 sensor 2 °C (OBD PID 0172). */
        val catalystB2s2TempC: Float? = null,
        /** Catalyst temp bank 1 sensor 3 °C (OBD PID 0173). */
        val catalystB1s3TempC: Float? = null,
        /** Catalyst temp bank 2 sensor 3 °C (OBD PID 0174). */
        val catalystB2s3TempC: Float? = null,
        /** Catalyst temp bank 1 sensor 4 °C (OBD PID 0175). */
        val catalystB1s4TempC: Float? = null,
        /** Catalyst temp bank 2 sensor 4 °C (OBD PID 0176). */
        val catalystB2s4TempC: Float? = null,
        /** STFT secondary O2 B1 % (OBD PID 0155), signed. */
        val fuelTrimStft2B1Pct: Float? = null,
        /** LTFT secondary O2 B1 % (OBD PID 0156), signed. */
        val fuelTrimLtft2B1Pct: Float? = null,
        /** STFT secondary O2 B2 % (OBD PID 0157), signed. */
        val fuelTrimStft2B2Pct: Float? = null,
        /** LTFT secondary O2 B2 % (OBD PID 0158), signed. */
        val fuelTrimLtft2B2Pct: Float? = null,
        /** Catalyst temp bank 1 sensor 5 °C (OBD PID 0177). */
        val catalystB1s5TempC: Float? = null,
        /** Catalyst temp bank 2 sensor 5 °C (OBD PID 0178). */
        val catalystB2s5TempC: Float? = null,
        /** Fuel injection timing ° (OBD PID 015D), signed. */
        val fuelInjectTimingDeg: Float? = null,
        /** Hybrid pack remaining life % (OBD PID 015B). */
        val hybridBattLifePct: Float? = null,
        /** Engine reference torque Nm (OBD PID 0163). */
        val engineRefTorqueNm: Float? = null,
        /** Catalyst temp bank 1 sensor 6 °C (OBD PID 0179). */
        val catalystB1s6TempC: Float? = null,
        /** Catalyst temp bank 2 sensor 6 °C (OBD PID 017A). */
        val catalystB2s6TempC: Float? = null,
        /** Absolute throttle B % (OBD PID 0147). */
        val throttleBPct: Float? = null,
        /** Absolute throttle C % (OBD PID 0148). */
        val throttleCPct: Float? = null,
        /** Time run with MIL on min (OBD PID 0154). */
        val milTimeMin: Int? = null,
        /** Catalyst temp bank 1 sensor 7 °C (OBD PID 017B). */
        val catalystB1s7TempC: Float? = null,
        /** Catalyst temp bank 2 sensor 7 °C (OBD PID 017C). */
        val catalystB2s7TempC: Float? = null,
        /** Fuel type code (OBD PID 0151). */
        val fuelTypeCode: Int? = null,
        /** Max equivalence ratio (OBD PID 014F). */
        val maxEquivRatio: Float? = null,
        /** Max MAF g/s (OBD PID 0150). */
        val maxMafGps: Float? = null,
        /** Run time since engine start (OBD PID 011F), seconds. */
    val runtimeSec: Int? = null,
    /** Distance with MIL on (OBD PID 0121), km. */
    val milDistanceKm: Float? = null,
    /** Distance since DTC cleared (OBD PID 0131), km. */
    val distSinceClearKm: Float? = null,
    /** MIL (check engine) from OBD PID 0101. */
    val mil: Boolean = false,
    /** Reported DTC count (PID 0101 or list size). */
    val dtcCount: Int = 0,
    /** Stored / pending / permanent codes. */
    val dtcs: List<ObdDtc.Code> = emptyList(),
    /** Rear ultrasonic distances (m) — live or sim. */
    val ussRearL: Float? = null,
    val ussRearC: Float? = null,
    val ussRearR: Float? = null,
    val source: String = "idle",
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    val reverse: Boolean get() = gear == Gear.R
    val speedKmh: Float get() = speedMps * 3.6f
    val anyDoorOpen: Boolean get() = doorFl || doorFr || doorRl || doorRr || trunkOpen || hoodOpen

    val tpmsLow: Boolean
        get() {
            val all = listOfNotNull(tpmsFlPsi, tpmsFrPsi, tpmsRlPsi, tpmsRrPsi)
            return all.any { it < 28f }
        }

    fun toJsonMap(): Map<String, Any?> =
        mapOf(
            "speed_mps" to speedMps.toDouble(),
            "speed_kmh" to speedKmh.toDouble(),
            "gear" to gear.name,
            "reverse" to reverse,
            "turn" to turn.name.lowercase(),
            "doors" to
                mapOf(
                    "fl" to doorFl,
                    "fr" to doorFr,
                    "rl" to doorRl,
                    "rr" to doorRr,
                    "trunk" to trunkOpen,
                    "hood" to hoodOpen,
                ),
            "parking_brake" to parkingBrake,
            "seatbelt_driver" to seatbeltDriver,
            "battery_soc_pct" to batterySocPct?.toDouble(),
            "fuel_pct" to fuelPct?.toDouble(),
            "fuel_rate_gps" to fuelRateGps?.toDouble(),
            "range_km" to rangeKm?.toDouble(),
            "rpm" to rpm?.toDouble(),
            "steering_angle_deg" to steeringAngleDeg?.toDouble(),
            "coolant_c" to coolantC?.toDouble(),
            "oil_temp_c" to oilTempC?.toDouble(),
            "intake_air_c" to intakeAirC?.toDouble(),
            "battery_voltage_v" to batteryVoltageV?.toDouble(),
            "outdoor_temp_c" to outdoorTempC?.toDouble(),
            "ignition" to ignition.name.lowercase(),
            "heading_deg" to headingDeg?.toDouble(),
            "yaw_rate_deg_s" to yawRateDegS?.toDouble(),
            "odometer_km" to odometerKm?.toDouble(),
            "abs_active" to absActive,
            "tpms" to
                mapOf(
                    "fl_psi" to tpmsFlPsi?.toDouble(),
                    "fr_psi" to tpmsFrPsi?.toDouble(),
                    "rl_psi" to tpmsRlPsi?.toDouble(),
                    "rr_psi" to tpmsRrPsi?.toDouble(),
                    "low" to tpmsLow,
                ),
            "hvac" to
                mapOf(
                    "cabin_c" to hvacCabinC?.toDouble(),
                    "target_c" to hvacTargetC?.toDouble(),
                    "ac_on" to hvacAcOn,
                    "fan" to hvacFanLevel,
                ),
            "throttle_pct" to throttlePct?.toDouble(),
            "engine_load_pct" to engineLoadPct?.toDouble(),
            "fuel_trim_stft_pct" to fuelTrimStftPct?.toDouble(),
            "fuel_trim_ltft_pct" to fuelTrimLtftPct?.toDouble(),
            "map_kpa" to mapKpa?.toDouble(),
            "catalyst_temp_c" to catalystTempC?.toDouble(),
            "maf_gps" to mafGps?.toDouble(),
            "fuel_pressure_kpa" to fuelPressureKpa?.toDouble(),
            "baro_kpa" to baroKpa?.toDouble(),
            "timing_advance_deg" to timingAdvanceDeg?.toDouble(),
            "o2_b1s1_volts" to o2B1s1Volts?.toDouble(),
            "absolute_load_pct" to absoluteLoadPct?.toDouble(),
            "relative_throttle_pct" to relativeThrottlePct?.toDouble(),
            "accel_pedal_pct" to accelPedalPct?.toDouble(),
            "o2_b1s2_volts" to o2B1s2Volts?.toDouble(),
            "egr_error_pct" to egrErrorPct?.toDouble(),
            "equiv_ratio" to equivRatio?.toDouble(),
            "evap_purge_pct" to evapPurgePct?.toDouble(),
            "ethanol_pct" to ethanolPct?.toDouble(),
            "evap_vapor_pa" to evapVaporPa?.toDouble(),
            "fuel_rail_abs_kpa" to fuelRailAbsKpa?.toDouble(),
            "egr_cmd_pct" to egrCmdPct?.toDouble(),
            "rel_accel_pedal_pct" to relAccelPedalPct?.toDouble(),
            "driver_torque_pct" to driverTorquePct?.toDouble(),
            "actual_torque_pct" to actualTorquePct?.toDouble(),
            "catalyst_b2_temp_c" to catalystB2TempC?.toDouble(),
            "catalyst_b1s2_temp_c" to catalystB1s2TempC?.toDouble(),
            "catalyst_b2s2_temp_c" to catalystB2s2TempC?.toDouble(),
            "catalyst_b1s3_temp_c" to catalystB1s3TempC?.toDouble(),
            "catalyst_b2s3_temp_c" to catalystB2s3TempC?.toDouble(),
            "catalyst_b1s4_temp_c" to catalystB1s4TempC?.toDouble(),
            "catalyst_b2s4_temp_c" to catalystB2s4TempC?.toDouble(),
            "fuel_trim_stft2_b1_pct" to fuelTrimStft2B1Pct?.toDouble(),
            "fuel_trim_ltft2_b1_pct" to fuelTrimLtft2B1Pct?.toDouble(),
            "fuel_trim_stft2_b2_pct" to fuelTrimStft2B2Pct?.toDouble(),
            "fuel_trim_ltft2_b2_pct" to fuelTrimLtft2B2Pct?.toDouble(),
            "catalyst_b1s5_temp_c" to catalystB1s5TempC?.toDouble(),
            "catalyst_b2s5_temp_c" to catalystB2s5TempC?.toDouble(),
            "fuel_inject_timing_deg" to fuelInjectTimingDeg?.toDouble(),
            "hybrid_batt_life_pct" to hybridBattLifePct?.toDouble(),
            "engine_ref_torque_nm" to engineRefTorqueNm?.toDouble(),
            "catalyst_b1s6_temp_c" to catalystB1s6TempC?.toDouble(),
            "catalyst_b2s6_temp_c" to catalystB2s6TempC?.toDouble(),
            "throttle_b_pct" to throttleBPct?.toDouble(),
            "throttle_c_pct" to throttleCPct?.toDouble(),
            "mil_time_min" to milTimeMin,
            "catalyst_b1s7_temp_c" to catalystB1s7TempC?.toDouble(),
            "catalyst_b2s7_temp_c" to catalystB2s7TempC?.toDouble(),
            "fuel_type_code" to fuelTypeCode,
            "max_equiv_ratio" to maxEquivRatio?.toDouble(),
            "max_maf_gps" to maxMafGps?.toDouble(),
            "runtime_sec" to runtimeSec,
            "mil_distance_km" to milDistanceKm?.toDouble(),
            "dist_since_clear_km" to distSinceClearKm?.toDouble(),
            "mil" to mil,
            "dtc_count" to dtcCount,
            "dtcs" to dtcs.map { it.toJsonMap() },
            "uss" to
                mapOf(
                    "rear_l_m" to ussRearL?.toDouble(),
                    "rear_c_m" to ussRearC?.toDouble(),
                    "rear_r_m" to ussRearR?.toDouble(),
                ),
            "source" to source,
            "updated_at_ms" to updatedAtMs,
        )
}
