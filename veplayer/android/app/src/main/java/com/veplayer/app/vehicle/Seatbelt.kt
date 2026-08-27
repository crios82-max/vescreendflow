package com.veplayer.app.vehicle

/**
 * Driver seatbelt unbuckled while moving.
 */
object Seatbelt {
    data class State(
        val buckled: Boolean = true,
        val speedKmh: Float = 0f,
        /** ok | unlatched | warn | alert */
        val band: String = "ok",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        signals: VehicleSignals,
        warnKmh: Float = 5f,
        alertKmh: Float = 15f,
    ): State {
        val speed = signals.speedKmh.coerceAtLeast(0f)
        val buckled = signals.seatbeltDriver
        if (buckled) {
            return State(buckled = true, speedKmh = speed, band = "ok", label = "")
        }
        val band =
            when {
                signals.reverse || speed >= alertKmh -> "alert"
                speed >= warnKmh -> "warn"
                else -> "unlatched"
            }
        return State(
            buckled = false,
            speedKmh = speed,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "Cinturón",
        )
    }

    fun voicePhrase(st: State): String =
        when (st.band) {
            "alert" -> "Atención. Abróchate el cinturón. Vehículo en movimiento."
            "warn" -> "Cuidado. Cinturón desabrochado."
            else -> "Cinturón desabrochado."
        }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFFE11D48
            "warn" -> 0xFFF59E0B
            "unlatched" -> 0xFFEAB308
            else -> 0xFF94A3B8
        }
}
