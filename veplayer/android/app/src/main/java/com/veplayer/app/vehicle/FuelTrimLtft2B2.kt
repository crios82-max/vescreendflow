package com.veplayer.app.vehicle

/** LTFT secondary O2 bank 2 (OBD PID 0158), signed %. */
object FuelTrimLtft2B2 {
    data class State(val trimPct: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun formatPct(pct: Float): String {
        val r = pct.toInt()
        return if (r >= 0) "+$r%" else "$r%"
    }

    fun evaluate(trimPct: Float?, speedKmh: Float = 0f, warnPct: Float = 12f, alertPct: Float = 20f, speedMinKmh: Float = 20f): State {
        if (trimPct == null) return State(band = "idle", label = "")
        val trim = trimPct.coerceIn(-50f, 50f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(5f, 40f)
        val alert = alertPct.coerceAtLeast(warn + 3f).coerceAtMost(50f)
        val abs = kotlin.math.abs(trim)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) return State(trimPct = trim, speedKmh = speed, band = "ok", label = if (abs >= 8f) "LT2B2 · ${formatPct(trim)}" else "")
        val band = when { abs >= alert -> "alert"; abs >= warn -> "warn"; else -> "ok" }
        return State(trimPct = trim, speedKmh = speed, band = band, showWarn = band != "ok", label = "LT2B2 · ${formatPct(trim)}")
    }

    fun voicePhrase(st: State): String {
        val p = st.trimPct?.let { formatPct(it) } ?: "alto"
        return when (st.band) {
            "alert" -> "Atención. LTFT O2 secundario B2 crítico. $p."
            "warn" -> "Cuidado. LTFT O2 secundario B2 fuera de rango. $p."
            else -> "LTFT O2 secundario B2 a $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("trim_pct" to st.trimPct?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
