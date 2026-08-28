package com.veplayer.app.vehicle

/** MAF sensor intake air temp °C (OBD PID 0166). */
object MafSensorIat {
    data class State(val tempC: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(tempC: Float?, speedKmh: Float = 0f, warnC: Float = 70f, alertC: Float = 85f, speedMinKmh: Float = 15f): State {
        if (tempC == null) return State(band = "idle", label = "")
        val c = tempC.coerceIn(-40f, 120f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnC.coerceIn(40f, 100f)
        val alert = alertC.coerceAtLeast(warn + 5f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(tempC = c, speedKmh = speed, band = "ok", label = if (c >= 45f) "MafIAT · ${c.toInt()}°C" else "")
        }
        val band = when { c >= alert -> "alert"; c >= warn -> "warn"; else -> "ok" }
        return State(tempC = c, speedKmh = speed, band = band, showWarn = band != "ok", label = "MafIAT · ${c.toInt()}°C")
    }

    fun voicePhrase(st: State): String {
        val c = st.tempC?.toInt()?.let { "$it grados" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. MAF IAT crítica. $c."
            "warn" -> "Cuidado. MAF IAT caliente. $c."
            else -> "MAF IAT a $c."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("temp_c" to st.tempC?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
