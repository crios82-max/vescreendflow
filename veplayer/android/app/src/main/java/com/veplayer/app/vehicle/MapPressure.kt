package com.veplayer.app.vehicle

/**
 * Intake manifold absolute pressure bands (OBD PID 010B), kPa.
 */
object MapPressure {
    data class State(
        val mapKpa: Float? = null,
        val speedKmh: Float = 0f,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        mapKpa: Float?,
        speedKmh: Float = 0f,
        warnKpa: Float = 95f,
        alertKpa: Float = 105f,
        speedMinKmh: Float = 20f,
    ): State {
        if (mapKpa == null) {
            return State(band = "idle", label = "")
        }
        val map = mapKpa.coerceIn(0f, 255f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnKpa.coerceIn(50f, 200f)
        val alert = alertKpa.coerceAtLeast(warn + 5f).coerceAtMost(255f)
        val minSpd = speedMinKmh.coerceIn(0f, 60f)

        if (speed < minSpd) {
            return State(
                mapKpa = map,
                speedKmh = speed,
                band = "ok",
                label = if (map >= 70f) "MAP · ${map.toInt()} kPa" else "",
            )
        }

        val band =
            when {
                map >= alert -> "alert"
                map >= warn -> "warn"
                else -> "ok"
            }
        return State(
            mapKpa = map,
            speedKmh = speed,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "MAP · ${map.toInt()} kPa",
        )
    }

    fun voicePhrase(st: State): String {
        val k = st.mapKpa?.toInt()?.let { "$it kilopascales" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Presión MAP crítica. $k. Reduce demanda."
            "warn" -> "Cuidado. Presión MAP alta. $k."
            else -> "Presión MAP a $k."
        }
    }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFFE11D48
            "warn" -> 0xFFF97316
            "ok" -> 0xFF14B8A6
            else -> 0xFF94A3B8
        }

    fun toJsonMap(st: State): Map<String, Any?> =
        mapOf(
            "map_kpa" to st.mapKpa?.toDouble(),
            "speed_kmh" to st.speedKmh.toDouble(),
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
