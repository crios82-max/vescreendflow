package com.veplayer.app.vehicle

/** EGT bank 2 sensor 5 (OBD PID 0199 bytes B/C), °C. */
object EgtB2S5 {
    data class State(val egtTempC: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(egtTempC: Float?, warnC: Float = 750f, alertC: Float = 850f): State {
        if (egtTempC == null) return State(band = "idle", label = "")
        val c = egtTempC.coerceIn(-40f, 1200f)
        val warn = warnC.coerceAtLeast(400f)
        val alert = alertC.coerceAtLeast(warn + 10f)
        val band = when { c >= alert -> "alert"; c >= warn -> "warn"; else -> "ok" }
        return State(egtTempC = c, band = band, showWarn = band != "ok", label = "EGTB2S5 · ${c.toInt()}°C")
    }

    fun voicePhrase(st: State): String {
        val c = st.egtTempC?.toInt()?.let { "$it grados" } ?: "elevada"
        return when (st.band) {
            "alert" -> "Atención. EGT B2S5 crítico. $c."
            "warn" -> "Cuidado. EGT B2S5 caliente. $c."
            else -> "EGT B2S5 a $c."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("egt_temp_c" to st.egtTempC?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
