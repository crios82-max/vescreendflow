package com.veplayer.app.vehicle

/** OBD odometer km (OBD PID 01A6 bytes A-D). */
object ObdOdometer {
    data class State(val odometerKm: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(odometerKm: Float?, speedKmh: Float = 0f, warnKm: Float = 120000f, alertKm: Float = 160000f, speedMinKmh: Float = 20f): State {
        if (odometerKm == null) return State(band = "idle", label = "")
        val km = odometerKm.coerceIn(0f, 4.3e8f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnKm.coerceIn(10000f, 500000f)
        val alert = alertKm.coerceAtLeast(warn + 5000f).coerceAtMost(500000f)
        val label = "Odo · ${km.toInt()}"
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(odometerKm = km, speedKmh = speed, band = "ok", label = if (km >= warn - 5000f) label else "")
        }
        val band = when { km >= alert -> "alert"; km >= warn -> "warn"; else -> "ok" }
        return State(odometerKm = km, speedKmh = speed, band = band, showWarn = band != "ok", label = label)
    }

    fun voicePhrase(st: State): String {
        val k = st.odometerKm?.toInt()?.let { "$it km" } ?: "alto"
        return when (st.band) {
            "alert" -> "Atención. Odómetro OBD crítico. $k."
            "warn" -> "Cuidado. Odómetro OBD alto. $k."
            else -> "Odómetro OBD a $k."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("odometer_km" to st.odometerKm?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
