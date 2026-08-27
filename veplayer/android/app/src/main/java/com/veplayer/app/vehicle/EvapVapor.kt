package com.veplayer.app.vehicle

/** Evap system vapor pressure (OBD PID 0153), Pa (signed). */
object EvapVapor {
    data class State(
        val pressurePa: Float? = null,
        val speedKmh: Float = 0f,
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        pressurePa: Float?,
        speedKmh: Float = 0f,
        warnAbsPa: Float = 5000f,
        alertAbsPa: Float = 8000f,
        speedMinKmh: Float = 20f,
    ): State {
        if (pressurePa == null) return State(band = "idle", label = "")
        val pa = pressurePa.coerceIn(-32768f, 32767f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnAbsPa.coerceIn(1000f, 15000f)
        val alert = alertAbsPa.coerceAtLeast(warn + 500f).coerceAtMost(20000f)
        val minSpd = speedMinKmh.coerceIn(0f, 60f)
        val absPa = kotlin.math.abs(pa)
        if (speed < minSpd) {
            return State(
                pressurePa = pa,
                speedKmh = speed,
                band = "ok",
                label = if (absPa >= 500f) "Vapor · ${pa.toInt()} Pa" else "",
            )
        }
        val band =
            when {
                absPa >= alert -> "alert"
                absPa >= warn -> "warn"
                else -> "ok"
            }
        return State(
            pressurePa = pa,
            speedKmh = speed,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "Vapor · ${pa.toInt()} Pa",
        )
    }

    fun voicePhrase(st: State): String {
        val p = st.pressurePa?.toInt()?.let { "$it pascales" } ?: "anómala"
        return when (st.band) {
            "alert" -> "Atención. Presión vapor evaporativo crítica. $p."
            "warn" -> "Cuidado. Presión vapor evaporativo alta. $p."
            else -> "Presión vapor a $p."
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
            "pressure_pa" to st.pressurePa?.toDouble(),
            "speed_kmh" to st.speedKmh.toDouble(),
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
