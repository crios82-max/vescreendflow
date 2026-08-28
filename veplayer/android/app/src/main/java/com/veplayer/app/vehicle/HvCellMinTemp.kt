package com.veplayer.app.vehicle

/** HEV min cell temperature °C (OBD PID 01B7 byte A). */
object HvCellMinTemp {
    data class State(val tempC: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(tempC: Float?, warnC: Float = 0f, alertC: Float = -5f): State {
        if (tempC == null) return State(band = "idle", label = "")
        val t = tempC.coerceIn(-40f, 120f)
        val warn = warnC.coerceIn(-30f, 20f)
        val alert = alertC.coerceAtMost(warn - 2f)
        val band = when { t <= alert -> "alert"; t <= warn -> "warn"; else -> "ok" }
        return State(tempC = t, band = band, showWarn = band != "ok", label = "HvMin · ${t.toInt()}°")
    }

    fun voicePhrase(st: State): String {
        val t = st.tempC?.toInt()?.let { "$it grados" } ?: "baja"
        return when (st.band) {
            "alert" -> "Atención. Celda mínima crítica. $t."
            "warn" -> "Cuidado. Celda mínima fría. $t."
            else -> "Celda mínima a $t."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("temp_c" to st.tempC?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
