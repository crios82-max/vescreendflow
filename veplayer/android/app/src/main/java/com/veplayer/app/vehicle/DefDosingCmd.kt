package com.veplayer.app.vehicle

/** Commanded DEF dosing % (OBD PID 01A5 byte B / 2). */
object DefDosingCmd {
    data class State(val dosePct: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(dosePct: Float?, speedKmh: Float = 0f, warnPct: Float = 60f, alertPct: Float = 90f, speedMinKmh: Float = 20f): State {
        if (dosePct == null) return State(band = "idle", label = "")
        val p = dosePct.coerceIn(0f, 127.5f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(20f, 100f)
        val alert = alertPct.coerceAtLeast(warn + 5f).coerceAtMost(127.5f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(dosePct = p, speedKmh = speed, band = "ok", label = if (p >= warn - 5f) "DEFDose · ${p.toInt()}%" else "")
        }
        val band = when { p >= alert -> "alert"; p >= warn -> "warn"; else -> "ok" }
        return State(dosePct = p, speedKmh = speed, band = band, showWarn = band != "ok", label = "DEFDose · ${p.toInt()}%")
    }

    fun voicePhrase(st: State): String {
        val p = st.dosePct?.toInt()?.let { "$it por ciento" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Dosificación DEF crítica. $p."
            "warn" -> "Cuidado. Dosificación DEF alta. $p."
            else -> "Dosificación DEF a $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("dose_pct" to st.dosePct?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
