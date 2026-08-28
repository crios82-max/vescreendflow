package com.veplayer.app.vehicle

/** NOx reagent quality counter hours (OBD PID 0194 bytes C/D). */
object NoxReagentQuality {
    data class State(val qualHours: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(qualHours: Float?, warnH: Float = 10f, alertH: Float = 20f): State {
        if (qualHours == null) return State(band = "idle", label = "")
        val h = qualHours.coerceIn(0f, 65535f)
        val warn = warnH.coerceIn(1f, 1000f)
        val alert = alertH.coerceAtLeast(warn + 2f)
        val band = when { h >= alert -> "alert"; h >= warn -> "warn"; else -> "ok" }
        return State(qualHours = h, band = band, showWarn = band != "ok", label = "NOxReq · ${h.toInt()}h")
    }

    fun voicePhrase(st: State): String {
        val h = st.qualHours?.toInt()?.let { "$it horas" } ?: "elevadas"
        return when (st.band) {
            "alert" -> "Atención. Calidad reactivo NOx crítica. $h."
            "warn" -> "Cuidado. Calidad reactivo NOx baja. $h."
            else -> "Reactivo NOx a $h."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("qual_hours" to st.qualHours?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
