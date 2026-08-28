package com.veplayer.app.vehicle

/** Diesel intake air flow commanded % (OBD PID 016A byte A). */
object DieselIntakeAirflow {
    data class State(val flowPct: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(flowPct: Float?, speedKmh: Float = 0f, warnPct: Float = 75f, alertPct: Float = 88f, speedMinKmh: Float = 15f): State {
        if (flowPct == null) return State(band = "idle", label = "")
        val p = flowPct.coerceIn(0f, 100f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(40f, 95f)
        val alert = alertPct.coerceAtLeast(warn + 3f).coerceAtMost(100f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(flowPct = p, speedKmh = speed, band = "ok", label = if (p >= 50f) "DslIAF · ${p.toInt()}%" else "")
        }
        val band = when { p >= alert -> "alert"; p >= warn -> "warn"; else -> "ok" }
        return State(flowPct = p, speedKmh = speed, band = band, showWarn = band != "ok", label = "DslIAF · ${p.toInt()}%")
    }

    fun voicePhrase(st: State): String {
        val p = st.flowPct?.toInt()?.let { "$it por ciento" } ?: "alto"
        return when (st.band) {
            "alert" -> "Atención. Flujo diesel IAF crítico. $p."
            "warn" -> "Cuidado. Flujo diesel IAF alto. $p."
            else -> "Diesel IAF a $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("flow_pct" to st.flowPct?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
