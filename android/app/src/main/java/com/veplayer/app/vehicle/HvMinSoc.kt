package com.veplayer.app.vehicle

/** Recommended minimum SOC % (OBD PID 01BF byte A). High recommended-min = restricted pack. */
object HvMinSoc {
    data class State(val socPct: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(socPct: Float?, warnPct: Float = 25f, alertPct: Float = 35f): State {
        if (socPct == null) return State(band = "idle", label = "")
        val p = socPct.coerceIn(0f, 100f)
        val warn = warnPct.coerceIn(5f, 60f)
        val alert = alertPct.coerceAtLeast(warn + 5f).coerceAtMost(80f)
        val band = when { p >= alert -> "alert"; p >= warn -> "warn"; else -> "ok" }
        return State(socPct = p, band = band, showWarn = band != "ok", label = "HvMinSOC · ${p.toInt()}%")
    }

    fun voicePhrase(st: State): String {
        val p = st.socPct?.toInt()?.let { "$it por ciento" } ?: "alto"
        return when (st.band) {
            "alert" -> "Atención. SOC mínimo recomendado HV restringido. $p."
            "warn" -> "Cuidado. SOC mínimo recomendado HV elevado. $p."
            else -> "SOC mínimo recomendado HV a $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("soc_pct" to st.socPct?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
