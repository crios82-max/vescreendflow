package com.veplayer.app.vehicle

/** Engine coolant sensor 2 °C (OBD PID 0167 byte C), radiator / ECT2. */
object CoolantEct2 {
    data class State(val coolantC: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(coolantC: Float?, warnC: Float = 95f, alertC: Float = 105f): State {
        if (coolantC == null) return State(band = "idle", label = "")
        val c = coolantC.coerceIn(-40f, 215f)
        val warn = warnC.coerceAtLeast(80f)
        val alert = alertC.coerceAtLeast(warn + 1f)
        val band = when { c >= alert -> "alert"; c >= warn -> "warn"; else -> "ok" }
        return State(coolantC = c, band = band, showWarn = band != "ok", label = "ECT2 · ${c.toInt()}°C")
    }

    fun voicePhrase(st: State): String {
        val c = st.coolantC?.toInt()?.let { "$it grados" } ?: "elevada"
        return when (st.band) {
            "alert" -> "Atención. Refrigerante ECT2 crítico. $c."
            "warn" -> "Cuidado. Refrigerante ECT2 caliente. $c."
            else -> "ECT2 a $c."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("coolant_c" to st.coolantC?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
