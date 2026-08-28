package com.veplayer.app.vehicle

/** O2 concentration bank 1 sensor 4 % (OBD PID 019C bytes D/E). */
object O2ConcB1S4 {
    data class State(val concPct: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(concPct: Float?, speedKmh: Float = 0f, warnPct: Float = 12f, alertPct: Float = 18f, speedMinKmh: Float = 20f): State {
        if (concPct == null) return State(band = "idle", label = "")
        val p = concPct.coerceIn(0f, 100f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(5f, 50f)
        val alert = alertPct.coerceAtLeast(warn + 2f).coerceAtMost(80f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(concPct = p, speedKmh = speed, band = "ok", label = if (p >= warn - 2f) "O2C4 · ${fmt(p)}%" else "")
        }
        val band = when { p >= alert -> "alert"; p >= warn -> "warn"; else -> "ok" }
        return State(concPct = p, speedKmh = speed, band = band, showWarn = band != "ok", label = "O2C4 · ${fmt(p)}%")
    }

    private fun fmt(p: Float): String = String.format("%.1f", p)

    fun voicePhrase(st: State): String {
        val v = st.concPct?.let { fmt(it) } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. O2 concentración B1S4 crítica. $v por ciento."
            "warn" -> "Cuidado. O2 concentración B1S4 alta. $v por ciento."
            else -> "O2 concentración B1S4 a $v por ciento."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("conc_pct" to st.concPct?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
