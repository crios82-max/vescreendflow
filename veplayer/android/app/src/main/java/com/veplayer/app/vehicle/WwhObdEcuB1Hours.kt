package com.veplayer.app.vehicle

/** WWH-OBD ECU B1 counter hours (OBD PID 0191 bytes D/E). */
object WwhObdEcuB1Hours {
    data class State(val b1Hours: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(b1Hours: Float?, warnH: Float = 100f, alertH: Float = 200f): State {
        if (b1Hours == null) return State(band = "idle", label = "")
        val h = b1Hours.coerceIn(0f, 65535f)
        val warn = warnH.coerceIn(1f, 1000f)
        val alert = alertH.coerceAtLeast(warn + 5f)
        val band = when { h >= alert -> "alert"; h >= warn -> "warn"; else -> "ok" }
        return State(b1Hours = h, band = band, showWarn = band != "ok", label = "WwhB1 · ${h.toInt()}h")
    }

    fun voicePhrase(st: State): String {
        val h = st.b1Hours?.toInt()?.let { "$it horas" } ?: "elevadas"
        return when (st.band) {
            "alert" -> "Atención. Contador ECU B1 WWH crítico. $h."
            "warn" -> "Cuidado. Contador ECU B1 WWH alto. $h."
            else -> "Contador ECU B1 WWH a $h."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("b1_hours" to st.b1Hours?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
