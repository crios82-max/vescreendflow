package com.veplayer.app.vehicle

/**
 * Parking brake / EPB engaged while moving (driver error, not tow).
 */
object ParkingBrakeMoving {
    data class State(
        val parkingBrake: Boolean = false,
        val speedKmh: Float = 0f,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        parkingBrake: Boolean,
        speedKmh: Float,
        warnKmh: Float = 5f,
        alertKmh: Float = 15f,
    ): State {
        val speed = speedKmh.coerceAtLeast(0f)
        if (!parkingBrake) {
            return State(parkingBrake = false, speedKmh = speed, band = "ok", label = "")
        }
        val warn = warnKmh.coerceIn(1f, 40f)
        val alert = alertKmh.coerceAtLeast(warn + 1f)
        if (speed < warn) {
            return State(
                parkingBrake = true,
                speedKmh = speed,
                band = "idle",
                label = "Freno estacionamiento",
            )
        }
        val band = if (speed >= alert) "alert" else "warn"
        return State(
            parkingBrake = true,
            speedKmh = speed,
            band = band,
            showWarn = true,
            label = "Freno · ${speed.toInt()} km/h",
        )
    }

    fun voicePhrase(st: State): String =
        when (st.band) {
            "alert" ->
                "Atención. Freno de estacionamiento activado en movimiento. Suelta el freno."
            "warn" ->
                "Cuidado. Estás conduciendo con el freno de estacionamiento."
            else -> "Freno de estacionamiento activado."
        }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFFE11D48
            "warn" -> 0xFFF59E0B
            else -> 0xFF94A3B8
        }
}
