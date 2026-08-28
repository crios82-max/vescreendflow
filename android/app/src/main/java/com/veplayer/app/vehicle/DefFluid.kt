package com.veplayer.app.vehicle

/** Diesel exhaust fluid level % (OBD PID 019B byte D). */
object DefFluid {
    data class State(val defPct: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(defPct: Float?, warnPct: Float = 25f, alertPct: Float = 15f): State {
        if (defPct == null) return State(band = "idle", label = "")
        val p = defPct.coerceIn(0f, 100f)
        val warn = warnPct.coerceIn(5f, 50f)
        val alert = alertPct.coerceAtMost(warn - 5f).coerceAtLeast(5f)
        val band = when { p <= alert -> "alert"; p <= warn -> "warn"; else -> "ok" }
        return State(defPct = p, band = band, showWarn = band != "ok", label = if (p <= warn + 5f) "DEF · ${p.toInt()}%" else "")
    }

    fun voicePhrase(st: State): String {
        val p = st.defPct?.toInt()?.let { "$it por ciento" } ?: "bajo"
        return when (st.band) {
            "alert" -> "Atención. DEF crítico. $p."
            "warn" -> "Cuidado. DEF bajo. $p."
            else -> "DEF a $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("def_pct" to st.defPct?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
