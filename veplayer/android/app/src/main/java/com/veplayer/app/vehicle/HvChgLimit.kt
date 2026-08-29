package com.veplayer.app.vehicle

/** HEV charge current limit A (OBD PID 01BA bytes B/C signed). */
object HvChgLimit {
    data class State(val currentA: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(currentA: Float?, speedKmh: Float = 0f, warnA: Float = 30f, alertA: Float = 15f, speedMinKmh: Float = 15f): State {
        if (currentA == null) return State(band = "idle", label = "")
        val a = currentA.coerceIn(0f, 500f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnA.coerceIn(5f, 200f)
        val alert = alertA.coerceAtMost(warn - 5f).coerceAtLeast(1f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(currentA = a, speedKmh = speed, band = "ok", label = if (a <= warn) "HvChg · ${a.toInt()}A" else "")
        }
        val band = when { a <= alert -> "alert"; a <= warn -> "warn"; else -> "ok" }
        return State(currentA = a, speedKmh = speed, band = band, showWarn = band != "ok", label = "HvChg · ${a.toInt()}A")
    }

    fun voicePhrase(st: State): String {
        val a = st.currentA?.toInt()?.let { "$it amperios" } ?: "baja"
        return when (st.band) {
            "alert" -> "Atención. Límite carga HV crítico. $a."
            "warn" -> "Cuidado. Límite carga HV bajo. $a."
            else -> "Límite carga HV a $a."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("current_a" to st.currentA?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
