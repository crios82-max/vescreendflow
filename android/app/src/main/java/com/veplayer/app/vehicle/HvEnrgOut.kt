package com.veplayer.app.vehicle

/** Cumulative energy from HVESS kWh (OBD PID 01BC bytes A–D). */
object HvEnrgOut {
    data class State(val kwh: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(kwh: Float?, warnKwh: Float = 15000f, alertKwh: Float = 25000f): State {
        if (kwh == null) return State(band = "idle", label = "")
        val k = kwh.coerceIn(0f, 1e8f)
        val warn = warnKwh.coerceIn(100f, 1e7f)
        val alert = alertKwh.coerceAtLeast(warn + 100f)
        val band = when { k >= alert -> "alert"; k >= warn -> "warn"; else -> "ok" }
        return State(kwh = k, band = band, showWarn = band != "ok", label = "HvOut · ${k.toInt()}kWh")
    }

    fun voicePhrase(st: State): String {
        val k = st.kwh?.toInt()?.let { "$it kWh" } ?: "elevado"
        return when (st.band) {
            "alert" -> "Atención. Energía descargada HV crítica. $k."
            "warn" -> "Cuidado. Energía descargada HV alta. $k."
            else -> "Energía descargada HV a $k."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("kwh" to st.kwh?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
