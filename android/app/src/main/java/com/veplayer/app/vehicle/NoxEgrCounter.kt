package com.veplayer.app.vehicle

/** NOx EGR valve counter hours (OBD PID 0194 bytes I/J). */
object NoxEgrCounter {
    data class State(val egrHours: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(egrHours: Float?, warnH: Float = 50f, alertH: Float = 100f): State {
        if (egrHours == null) return State(band = "idle", label = "")
        val h = egrHours.coerceIn(0f, 65535f)
        val warn = warnH.coerceIn(1f, 1000f)
        val alert = alertH.coerceAtLeast(warn + 5f)
        val band = when { h >= alert -> "alert"; h >= warn -> "warn"; else -> "ok" }
        return State(egrHours = h, band = band, showWarn = band != "ok", label = "EGRcnt · ${h.toInt()}h")
    }

    fun voicePhrase(st: State): String {
        val h = st.egrHours?.toInt()?.let { "$it horas" } ?: "elevadas"
        return when (st.band) {
            "alert" -> "Atención. Contador EGR NOx crítico. $h."
            "warn" -> "Cuidado. Contador EGR NOx alto. $h."
            else -> "Contador EGR NOx a $h."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("egr_hours" to st.egrHours?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
