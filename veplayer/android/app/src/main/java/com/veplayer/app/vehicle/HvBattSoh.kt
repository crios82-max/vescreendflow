package com.veplayer.app.vehicle

/** Traction battery SOH % (OBD PID 01B2 byte A). */
object HvBattSoh {
    data class State(val sohPct: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(sohPct: Float?, warnPct: Float = 70f, alertPct: Float = 50f): State {
        if (sohPct == null) return State(band = "idle", label = "")
        val p = sohPct.coerceIn(0f, 100f)
        val warn = warnPct.coerceIn(20f, 90f)
        val alert = alertPct.coerceAtMost(warn - 5f).coerceAtLeast(10f)
        val band = when { p <= alert -> "alert"; p <= warn -> "warn"; else -> "ok" }
        return State(sohPct = p, band = band, showWarn = band != "ok", label = "HySOH · ${p.toInt()}%")
    }

    fun voicePhrase(st: State): String {
        val p = st.sohPct?.toInt()?.let { "$it por ciento" } ?: "bajo"
        return when (st.band) {
            "alert" -> "Atención. SOH batería tracción crítico. $p."
            "warn" -> "Cuidado. SOH batería tracción bajo. $p."
            else -> "SOH batería tracción a $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("soh_pct" to st.sohPct?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
