package com.veplayer.app.vehicle

/** Fuel injection timing (OBD PID 015D), degrees before TDC. */
object FuelInjectTiming {
    data class State(val timingDeg: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(timingDeg: Float?, speedKmh: Float = 0f, warnDeg: Float = 28f, alertDeg: Float = 40f, speedMinKmh: Float = 20f): State {
        if (timingDeg == null) return State(band = "idle", label = "")
        val deg = timingDeg.coerceIn(-64f, 64f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnDeg.coerceIn(10f, 55f)
        val alert = alertDeg.coerceAtLeast(warn + 4f).coerceAtMost(64f)
        val abs = kotlin.math.abs(deg)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) return State(timingDeg = deg, speedKmh = speed, band = "ok", label = if (abs >= 15f) "Inject · ${deg.toInt()}°" else "")
        val band = when { abs >= alert -> "alert"; abs >= warn -> "warn"; else -> "ok" }
        return State(timingDeg = deg, speedKmh = speed, band = band, showWarn = band != "ok", label = "Inject · ${deg.toInt()}°")
    }

    fun voicePhrase(st: State): String {
        val d = st.timingDeg?.toInt()?.let { "$it grados" } ?: "fuera de rango"
        return when (st.band) {
            "alert" -> "Atención. Inyección crítica. $d."
            "warn" -> "Cuidado. Inyección fuera de rango. $d."
            else -> "Inyección a $d."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("timing_deg" to st.timingDeg?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
