package com.veplayer.app.vehicle

/** Fuel rail absolute pressure (OBD PID 0159), kPa — low pressure alerts. */
object FuelRailAbs {
    data class State(
        val pressureKpa: Float? = null,
        val speedKmh: Float = 0f,
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        pressureKpa: Float?,
        speedKmh: Float = 0f,
        warnKpa: Float = 8000f,
        alertKpa: Float = 6000f,
        speedMinKmh: Float = 20f,
    ): State {
        if (pressureKpa == null) return State(band = "idle", label = "")
        val kpa = pressureKpa.coerceIn(0f, 655350f)
        val speed = speedKmh.coerceAtLeast(0f)
        val alert = alertKpa.coerceIn(2000f, 15000f)
        val warn = warnKpa.coerceAtLeast(alert + 500f).coerceAtMost(20000f)
        val minSpd = speedMinKmh.coerceIn(0f, 60f)
        if (speed < minSpd) {
            return State(
                pressureKpa = kpa,
                speedKmh = speed,
                band = "ok",
                label = if (kpa <= 12000f) "Rail · ${(kpa / 1000f).toInt()} MPa" else "",
            )
        }
        val band =
            when {
                kpa <= alert -> "alert"
                kpa <= warn -> "warn"
                else -> "ok"
            }
        val labelKpa = if (kpa >= 10000f) "Rail · ${(kpa / 1000f).toInt()} MPa" else "Rail · ${kpa.toInt()} kPa"
        return State(
            pressureKpa = kpa,
            speedKmh = speed,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = labelKpa,
        )
    }

    fun voicePhrase(st: State): String {
        val k = st.pressureKpa?.toInt()?.let { "$it kilopascales" } ?: "baja"
        return when (st.band) {
            "alert" -> "Atención. Presión rail absoluta crítica. $k."
            "warn" -> "Cuidado. Presión rail absoluta baja. $k."
            else -> "Presión rail a $k."
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
