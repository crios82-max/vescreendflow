package com.veplayer.app.vehicle

/** Fuel system 1 use % (OBD PID 019F byte B). */
object FuelSysUsePct1 {
    data class State(val usePct: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(usePct: Float?, speedKmh: Float = 0f, warnPct: Float = 70f, alertPct: Float = 85f, speedMinKmh: Float = 20f): State {
        if (usePct == null) return State(band = "idle", label = "")
        val p = usePct.coerceIn(0f, 100f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(20f, 98f)
        val alert = alertPct.coerceAtLeast(warn + 5f).coerceAtMost(100f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(usePct = p, speedKmh = speed, band = "ok", label = if (p >= warn - 5f) "FSu1 · ${p.toInt()}%" else "")
        }
        val band = when { p >= alert -> "alert"; p >= warn -> "warn"; else -> "ok" }
        return State(usePct = p, speedKmh = speed, band = band, showWarn = band != "ok", label = "FSu1 · ${p.toInt()}%")
    }

    fun voicePhrase(st: State): String {
        val p = st.usePct?.toInt()?.toString() ?: "alto"
        return when (st.band) {
            "alert" -> "Atención. Uso sistema combustible uno crítico. $p por ciento."
            "warn" -> "Cuidado. Uso sistema combustible uno alto. $p por ciento."
            else -> "Uso sistema combustible uno a $p por ciento."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("use_pct" to st.usePct?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
