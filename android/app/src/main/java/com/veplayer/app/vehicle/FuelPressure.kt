package com.veplayer.app.vehicle

/**
 * Fuel rail pressure bands (OBD PID 010A), kPa — low pressure alerts.
 */
object FuelPressure {
    data class State(
        val pressureKpa: Float? = null,
        val speedKmh: Float = 0f,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        pressureKpa: Float?,
        speedKmh: Float = 0f,
        warnKpa: Float = 280f,
        alertKpa: Float = 220f,
        speedMinKmh: Float = 20f,
    ): State {
        if (pressureKpa == null) {
            return State(band = "idle", label = "")
        }
        val kpa = pressureKpa.coerceIn(0f, 765f)
        val speed = speedKmh.coerceAtLeast(0f)
        val alert = alertKpa.coerceIn(100f, 400f)
        val warn = warnKpa.coerceAtLeast(alert + 20f).coerceAtMost(500f)
        val minSpd = speedMinKmh.coerceIn(0f, 60f)

        if (speed < minSpd) {
            return State(
                pressureKpa = kpa,
                speedKmh = speed,
                band = "ok",
                label = if (kpa <= 350f) "FuelP · ${kpa.toInt()} kPa" else "",
            )
        }

        val band =
            when {
                kpa <= alert -> "alert"
                kpa <= warn -> "warn"
                else -> "ok"
            }
        return State(
            pressureKpa = kpa,
            speedKmh = speed,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "FuelP · ${kpa.toInt()} kPa",
        )
    }

    fun voicePhrase(st: State): String {
        val k = st.pressureKpa?.toInt()?.let { "$it kilopascales" } ?: "baja"
        return when (st.band) {
            "alert" -> "Atención. Presión de combustible crítica. $k. Revisa bomba y filtro."
            "warn" -> "Cuidado. Presión de combustible baja. $k."
            else -> "Presión combustible a $k."
        }
    }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFFE11D48
            "warn" -> 0xFFF59E0B
            "ok" -> 0xFF14B8A6
            else -> 0xFF94A3B8
        }

    fun toJsonMap(st: State): Map<String, Any?> =
        mapOf(
            "pressure_kpa" to st.pressureKpa?.toDouble(),
            "speed_kmh" to st.speedKmh.toDouble(),
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
