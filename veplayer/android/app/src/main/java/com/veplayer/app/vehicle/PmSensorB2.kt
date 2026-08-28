package com.veplayer.app.vehicle

/** PM sensor normalized output B2S1 % (OBD PID 018F bytes F/G). */
object PmSensorB2 {
    data class State(val pmPct: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(pmPct: Float?, speedKmh: Float = 0f, warnPct: Float = 70f, alertPct: Float = 85f, speedMinKmh: Float = 15f): State {
        if (pmPct == null) return State(band = "idle", label = "")
        val p = pmPct.coerceIn(0f, 200f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(30f, 95f)
        val alert = alertPct.coerceAtLeast(warn + 5f).coerceAtMost(150f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(pmPct = p, speedKmh = speed, band = "ok", label = if (p >= 45f) "PMB2 · ${p.toInt()}%" else "")
        }
        val band = when { p >= alert -> "alert"; p >= warn -> "warn"; else -> "ok" }
        return State(pmPct = p, speedKmh = speed, band = band, showWarn = band != "ok", label = "PMB2 · ${p.toInt()}%")
    }

    fun voicePhrase(st: State): String {
        val p = st.pmPct?.toInt()?.let { "$it por ciento" } ?: "alto"
        return when (st.band) {
            "alert" -> "Atención. PM B2 crítico. $p."
            "warn" -> "Cuidado. PM B2 alto. $p."
            else -> "PM B2 a $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("pm_pct" to st.pmPct?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
