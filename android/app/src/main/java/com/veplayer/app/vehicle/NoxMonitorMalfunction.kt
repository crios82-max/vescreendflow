package com.veplayer.app.vehicle

/** NOx monitoring system malfunction counter hours (OBD PID 0194 bytes K/L). */
object NoxMonitorMalfunction {
    data class State(val malfHours: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(malfHours: Float?, warnH: Float = 50f, alertH: Float = 100f): State {
        if (malfHours == null) return State(band = "idle", label = "")
        val h = malfHours.coerceIn(0f, 65535f)
        val warn = warnH.coerceIn(1f, 1000f)
        val alert = alertH.coerceAtLeast(warn + 5f)
        val band = when { h >= alert -> "alert"; h >= warn -> "warn"; else -> "ok" }
        return State(malfHours = h, band = band, showWarn = band != "ok", label = "NOxMal · ${h.toInt()}h")
    }

    fun voicePhrase(st: State): String {
        val h = st.malfHours?.toInt()?.let { "$it horas" } ?: "elevadas"
        return when (st.band) {
            "alert" -> "Atención. Malfunction monitor NOx crítico. $h."
            "warn" -> "Cuidado. Malfunction monitor NOx alto. $h."
            else -> "Malfunction monitor NOx a $h."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("malf_hours" to st.malfHours?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
