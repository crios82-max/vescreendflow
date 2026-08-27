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
    val rangeKm: Float? = null,
    val rpm: Float? = null,
    val steeringAngleDeg: Float? = null,
    val coolantC: Float? = null,
    /** Engine oil temperature (OBD PID 015C). */
    val oilTempC: Float? = null,
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
        /** Run time since engine start (OBD PID 011F), seconds. */
    val runtimeSec: Int? = null,
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
            "range_km" to rangeKm?.toDouble(),
            "rpm" to rpm?.toDouble(),
            "steering_angle_deg" to steeringAngleDeg?.toDouble(),
            "coolant_c" to coolantC?.toDouble(),
            "oil_temp_c" to oilTempC?.toDouble(),
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
            "runtime_sec" to runtimeSec,
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
