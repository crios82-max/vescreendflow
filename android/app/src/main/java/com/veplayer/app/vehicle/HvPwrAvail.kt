package com.veplayer.app.vehicle

/** HEV continuous rated power available % (OBD PID 01BA byte A). */
object HvPwrAvail {
    data class State(val pct: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(pct: Float?, warnPct: Float = 25f, alertPct: Float = 15f): State {
        if (pct == null) return State(band = "idle", label = "")
        val p = pct.coerceIn(0f, 100f)
        val warn = warnPct.coerceIn(5f, 80f)
        val alert = alertPct.coerceAtMost(warn - 5f).coerceAtLeast(5f)
        val band = when { p <= alert -> "alert"; p <= warn -> "warn"; else -> "ok" }
        return State(pct = p, band = band, showWarn = band != "ok", label = "HvPwr · ${p.toInt()}%")
    }

    fun voicePhrase(st: State): String {
        val p = st.pct?.toInt()?.let { "$it por ciento" } ?: "bajo"
        return when (st.band) {
            "alert" -> "Atención. Potencia HV crítica. $p."
            "warn" -> "Cuidado. Potencia HV limitada. $p."
            else -> "Potencia HV a $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("pct" to st.pct?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
