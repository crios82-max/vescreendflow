package com.veplayer.app.vehicle

/** State of Certified Energy % (OBD PID 01D2 byte B ×100/255). */
object HvSoce {
    data class State(val socePct: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(socePct: Float?, warnPct: Float = 70f, alertPct: Float = 50f): State {
        if (socePct == null) return State(band = "idle", label = "")
        val p = socePct.coerceIn(0f, 100f)
        val warn = warnPct.coerceIn(20f, 90f)
        val alert = alertPct.coerceAtMost(warn - 5f).coerceAtLeast(10f)
        val band = when { p <= alert -> "alert"; p <= warn -> "warn"; else -> "ok" }
        return State(socePct = p, band = band, showWarn = band != "ok", label = "SOCE · ${p.toInt()}%")
    }

    fun voicePhrase(st: State): String {
        val p = st.socePct?.toInt()?.let { "$it por ciento" } ?: "bajo"
        return when (st.band) {
            "alert" -> "Atención. Energía certificada HV crítica. $p."
            "warn" -> "Cuidado. Energía certificada HV baja. $p."
            else -> "Energía certificada HV a $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("soce_pct" to st.socePct?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
