package com.veplayer.app.vehicle

/** Distance since reflash km (OBD PID 01C7 bytes A/B). */
object ReflashDistance {
    data class State(val distanceKm: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(distanceKm: Float?, speedKmh: Float = 0f, warnKm: Float = 5000f, alertKm: Float = 10000f, speedMinKmh: Float = 20f): State {
        if (distanceKm == null) return State(band = "idle", label = "")
        val km = distanceKm.coerceIn(0f, 65535f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnKm.coerceIn(500f, 50000f)
        val alert = alertKm.coerceAtLeast(warn + 500f).coerceAtMost(65535f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(distanceKm = km, speedKmh = speed, band = "ok", label = if (km >= warn - 500f) "Reflash · ${km.toInt()}" else "")
        }
        val band = when { km >= alert -> "alert"; km >= warn -> "warn"; else -> "ok" }
        return State(distanceKm = km, speedKmh = speed, band = band, showWarn = band != "ok", label = "Reflash · ${km.toInt()}")
    }

    fun voicePhrase(st: State): String {
        val k = st.distanceKm?.toInt()?.let { "$it km" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Distancia desde reflash crítica. $k."
            "warn" -> "Cuidado. Distancia desde reflash alta. $k."
            else -> "Distancia desde reflash a $k."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("distance_km" to st.distanceKm?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
