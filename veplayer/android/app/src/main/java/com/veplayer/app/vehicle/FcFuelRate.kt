package com.veplayer.app.vehicle

/** Fuel cell fuel rate g/s (OBD PID 01D5 bytes C/D /100). High = stress. */
object FcFuelRate {
    data class State(val gps: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(gps: Float?, warnGps: Float = 2f, alertGps: Float = 4f): State {
        if (gps == null) return State(band = "idle", label = "")
        val g = gps.coerceIn(0f, 100f)
        val warn = warnGps.coerceIn(0.2f, 20f)
        val alert = alertGps.coerceAtLeast(warn + 0.2f)
        val band = when { g >= alert -> "alert"; g >= warn -> "warn"; else -> "ok" }
        return State(gps = g, band = band, showWarn = band != "ok", label = "FcFuel · ${"%.2f".format(g)}g/s")
    }

    fun voicePhrase(st: State): String {
        val g = st.gps?.let { "${"%.2f".format(it)} gramos por segundo" } ?: "elevado"
        return when (st.band) {
            "alert" -> "Atención. Consumo celda de combustible crítico. $g."
            "warn" -> "Cuidado. Consumo celda de combustible alto. $g."
            else -> "Consumo celda de combustible a $g."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("gps" to st.gps?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
