package com.veplayer.app.vehicle

/**
 * Unauthorized movement / tow: vehicle moving while ignition off (or parking brake on).
 */
object UnauthorizedMove {
    data class State(
        val ignitionOn: Boolean = true,
        val parkingBrake: Boolean = false,
        val speedKmh: Float = 0f,
        /** Seconds moving while "secured". */
        val movingForSec: Float = 0f,
        /** ok | moving | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    /** Vehicle considered secured (should not move under power). */
    fun isSecured(
        ignitionOn: Boolean,
        parkingBrake: Boolean,
    ): Boolean = !ignitionOn || parkingBrake

    fun evaluate(
        ignitionOn: Boolean,
        parkingBrake: Boolean,
        speedKmh: Float,
        movingForSec: Float,
        speedMinKmh: Float = 3f,
        warnSec: Float = 3f,
        alertSec: Float = 8f,
    ): State {
        val speed = speedKmh.coerceAtLeast(0f)
        val secured = isSecured(ignitionOn, parkingBrake)
        if (!secured) {
            return State(
                ignitionOn = ignitionOn,
                parkingBrake = parkingBrake,
                speedKmh = speed,
                band = "ok",
                label = "",
            )
        }
        if (speed < speedMinKmh) {
            return State(
                ignitionOn = ignitionOn,
                parkingBrake = parkingBrake,
                speedKmh = speed,
                movingForSec = 0f,
                band = "idle",
                label = "",
            )
        }
        val moving = movingForSec.coerceAtLeast(0f)
        val band =
            when {
                moving >= alertSec -> "alert"
                moving >= warnSec -> "warn"
                else -> "moving"
            }
        return State(
            ignitionOn = ignitionOn,
            parkingBrake = parkingBrake,
            speedKmh = speed,
            movingForSec = moving,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "Remolque · ${speed.toInt()} km/h",
        )
    }

    fun voicePhrase(st: State): String =
        when (st.band) {
            "alert" ->
                "Atención. Vehículo en movimiento con el motor apagado. Posible remolque."
            "warn" ->
                "Cuidado. El vehículo se está moviendo sin ignición."
            else -> "Vehículo en movimiento."
        }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFFE11D48
            "warn" -> 0xFFF59E0B
            "moving" -> 0xFFEAB308
            else -> 0xFF94A3B8
        }
}
