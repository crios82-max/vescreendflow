package com.veplayer.app.vehicle

/** Fuel cell system voltage V (OBD PID 01D5 bytes A/B /10). Low = stress. */
object FcVolt {
    data class State(val volts: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(volts: Float?, warnV: Float = 200f, alertV: Float = 150f): State {
        if (volts == null) return State(band = "idle", label = "")
        val v = volts.coerceIn(0f, 1000f)
        val warn = warnV.coerceIn(50f, 500f)
        val alert = alertV.coerceAtMost(warn - 10f).coerceAtLeast(20f)
        val band = when { v <= alert -> "alert"; v <= warn -> "warn"; else -> "ok" }
        return State(volts = v, band = band, showWarn = band != "ok", label = "FcV · ${"%.0f".format(v)}V")
    }

    fun voicePhrase(st: State): String {
        val v = st.volts?.let { "${"%.0f".format(it)} voltios" } ?: "baja"
        return when (st.band) {
            "alert" -> "Atención. Voltaje celda de combustible crítico. $v."
            "warn" -> "Cuidado. Voltaje celda de combustible bajo. $v."
            else -> "Voltaje celda de combustible a $v."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("volts" to st.volts?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
