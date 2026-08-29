package com.veplayer.app.vehicle

/** HVESS actual charge rate kW (OBD PID 01B3 bytes A/B signed /10). */
object HvAcr {
    data class State(val kw: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(kw: Float?, warnKw: Float = 80f, alertKw: Float = 120f): State {
        if (kw == null) return State(band = "idle", label = "")
        val k = kotlin.math.abs(kw).coerceIn(0f, 500f)
        val warn = warnKw.coerceIn(10f, 300f)
        val alert = alertKw.coerceAtLeast(warn + 5f)
        val band = when { k >= alert -> "alert"; k >= warn -> "warn"; else -> "ok" }
        return State(kw = kw, band = band, showWarn = band != "ok", label = "HvAcr · ${"%.1f".format(kw)}kW")
    }

    fun voicePhrase(st: State): String {
        val k = st.kw?.let { "${"%.1f".format(it)} kilovatios" } ?: "elevada"
        return when (st.band) {
            "alert" -> "Atención. Tasa de carga HV crítica. $k."
            "warn" -> "Cuidado. Tasa de carga HV alta. $k."
            else -> "Tasa de carga HV a $k."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("kw" to st.kw?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
