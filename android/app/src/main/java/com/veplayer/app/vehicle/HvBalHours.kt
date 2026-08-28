package com.veplayer.app.vehicle

/** Hours since last cell balancing complete (OBD PID 01B8 bytes A/B). */
object HvBalHours {
    data class State(val hours: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(hours: Float?, warnH: Float = 100f, alertH: Float = 200f): State {
        if (hours == null) return State(band = "idle", label = "")
        val h = hours.coerceIn(0f, 65535f)
        val warn = warnH.coerceIn(10f, 1000f)
        val alert = alertH.coerceAtLeast(warn + 10f)
        val band = when { h >= alert -> "alert"; h >= warn -> "warn"; else -> "ok" }
        return State(hours = h, band = band, showWarn = band != "ok", label = "HvBal · ${h.toInt()}h")
    }

    fun voicePhrase(st: State): String {
        val h = st.hours?.toInt()?.let { "$it horas" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Tiempo sin balanceo crítico. $h."
            "warn" -> "Cuidado. Balanceo de celdas pendiente. $h."
            else -> "Balanceo hace $h."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("hours" to st.hours?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
