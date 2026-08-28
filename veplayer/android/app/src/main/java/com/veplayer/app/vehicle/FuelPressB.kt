package com.veplayer.app.vehicle

/** Fuel pressure sensor B kPa (OBD PID 01C5 bytes C/D). */
object FuelPressB {
    data class State(val pressureKpa: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(pressureKpa: Float?, speedKmh: Float = 0f, warnKpa: Float = 4000f, alertKpa: Float = 4800f, speedMinKmh: Float = 20f): State {
        if (pressureKpa == null) return State(band = "idle", label = "")
        val kpa = pressureKpa.coerceIn(0f, 5177f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnKpa.coerceIn(500f, 5000f)
        val alert = alertKpa.coerceAtLeast(warn + 200f).coerceAtMost(5177f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(pressureKpa = kpa, speedKmh = speed, band = "ok", label = if (kpa >= warn - 300f) "FPb · ${kpa.toInt()}" else "")
        }
        val band = when { kpa >= alert -> "alert"; kpa >= warn -> "warn"; else -> "ok" }
        return State(pressureKpa = kpa, speedKmh = speed, band = band, showWarn = band != "ok", label = "FPb · ${kpa.toInt()}")
    }

    fun voicePhrase(st: State): String {
        val k = st.pressureKpa?.toInt()?.let { "$it kPa" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Presión combustible B crítica. $k."
            "warn" -> "Cuidado. Presión combustible B alta. $k."
            else -> "Presión combustible B a $k."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("pressure_kpa" to st.pressureKpa?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
