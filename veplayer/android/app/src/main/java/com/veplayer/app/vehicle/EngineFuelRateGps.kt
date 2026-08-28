package com.veplayer.app.vehicle

/** Engine fuel rate g/s (OBD PID 019D bytes A/B). */
object EngineFuelRateGps {
    data class State(val rateGps: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(rateGps: Float?, speedKmh: Float = 0f, warnGps: Float = 3f, alertGps: Float = 5f, speedMinKmh: Float = 20f): State {
        if (rateGps == null) return State(band = "idle", label = "")
        val g = rateGps.coerceIn(0f, 500f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnGps.coerceIn(0.5f, 100f)
        val alert = alertGps.coerceAtLeast(warn + 0.5f).coerceAtMost(200f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(rateGps = g, speedKmh = speed, band = "ok", label = if (g >= warn - 0.5f) "FuelGPS · ${"%.1f".format(g)}" else "")
        }
        val band = when { g >= alert -> "alert"; g >= warn -> "warn"; else -> "ok" }
        return State(rateGps = g, speedKmh = speed, band = band, showWarn = band != "ok", label = "FuelGPS · ${"%.1f".format(g)}")
    }

    fun voicePhrase(st: State): String {
        val v = st.rateGps?.let { "%.1f".format(it) } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Tasa combustible motor crítica. $v gramos por segundo."
            "warn" -> "Cuidado. Tasa combustible motor alta. $v gramos por segundo."
            else -> "Tasa combustible motor a $v gramos por segundo."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("rate_gps" to st.rateGps?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
