package com.veplayer.app.vehicle

/** WWH-OBD cumulative continuous MI counter hours (OBD PID 0193 bytes B/C). */
object WwhObdCumulativeMi {
    data class State(val miHours: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(miHours: Float?, warnH: Float = 100f, alertH: Float = 200f): State {
        if (miHours == null) return State(band = "idle", label = "")
        val h = miHours.coerceIn(0f, 65535f)
        val warn = warnH.coerceIn(1f, 1000f)
        val alert = alertH.coerceAtLeast(warn + 5f)
        val band = when { h >= alert -> "alert"; h >= warn -> "warn"; else -> "ok" }
        return State(miHours = h, band = band, showWarn = band != "ok", label = "WwhCum · ${h.toInt()}h")
    }

    fun voicePhrase(st: State): String {
        val h = st.miHours?.toInt()?.let { "$it horas" } ?: "elevadas"
        return when (st.band) {
            "alert" -> "Atención. Contador MI acumulado WWH crítico. $h."
            "warn" -> "Cuidado. Contador MI acumulado WWH alto. $h."
            else -> "MI acumulado WWH a $h."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("mi_hours" to st.miHours?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
