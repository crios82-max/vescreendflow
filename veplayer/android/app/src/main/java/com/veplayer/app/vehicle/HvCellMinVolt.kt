package com.veplayer.app.vehicle

/** HEV min cell voltage V (OBD PID 01B9 bytes A/B). */
object HvCellMinVolt {
    data class State(val volts: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(volts: Float?, warnV: Float = 3.2f, alertV: Float = 3.0f): State {
        if (volts == null) return State(band = "idle", label = "")
        val v = volts.coerceIn(0f, 5f)
        val warn = warnV.coerceIn(2.5f, 4f)
        val alert = alertV.coerceIn(2f, warn - 0.05f)
        val band = when { v < alert -> "alert"; v < warn -> "warn"; else -> "ok" }
        val lbl = "HvMinV · ${"%.2f".format(v)}V"
        return State(volts = v, band = band, showWarn = band != "ok", label = lbl)
    }

    fun voicePhrase(st: State): String {
        val v = st.volts?.let { "%.2f".format(it) } ?: "baja"
        return when (st.band) {
            "alert" -> "Atención. Celda mínima crítica. $v voltios."
            "warn" -> "Cuidado. Celda mínima baja. $v voltios."
            else -> "Celda mínima a $v voltios."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("volts" to st.volts?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
