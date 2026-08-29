package com.veplayer.app.vehicle

/** Recommended maximum SOC % (OBD PID 01C1 byte A). Low recommended-max = charge limited. */
object HvMaxSoc {
    data class State(val socPct: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(socPct: Float?, warnPct: Float = 85f, alertPct: Float = 75f): State {
        if (socPct == null) return State(band = "idle", label = "")
        val p = socPct.coerceIn(0f, 100f)
        val warn = warnPct.coerceIn(50f, 98f)
        val alert = alertPct.coerceAtMost(warn - 5f).coerceAtLeast(40f)
        val band = when { p <= alert -> "alert"; p <= warn -> "warn"; else -> "ok" }
        return State(socPct = p, band = band, showWarn = band != "ok", label = "HvMaxSOC · ${p.toInt()}%")
    }

    fun voicePhrase(st: State): String {
        val p = st.socPct?.toInt()?.let { "$it por ciento" } ?: "bajo"
        return when (st.band) {
            "alert" -> "Atención. SOC máximo recomendado HV restringido. $p."
            "warn" -> "Cuidado. SOC máximo recomendado HV bajo. $p."
            else -> "SOC máximo recomendado HV a $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("soc_pct" to st.socPct?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
