package com.veplayer.app.vehicle

/** Actual EGR % (OBD PID 0169 byte A). */
object ActualEgr {
    data class State(val egrPct: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(egrPct: Float?, speedKmh: Float = 0f, warnPct: Float = 55f, alertPct: Float = 70f, speedMinKmh: Float = 15f): State {
        if (egrPct == null) return State(band = "idle", label = "")
        val p = egrPct.coerceIn(0f, 100f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(25f, 90f)
        val alert = alertPct.coerceAtLeast(warn + 3f).coerceAtMost(100f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(egrPct = p, speedKmh = speed, band = "ok", label = if (p >= 35f) "EgrAct · ${p.toInt()}%" else "")
        }
        val band = when { p >= alert -> "alert"; p >= warn -> "warn"; else -> "ok" }
        return State(egrPct = p, speedKmh = speed, band = band, showWarn = band != "ok", label = "EgrAct · ${p.toInt()}%")
    }

    fun voicePhrase(st: State): String {
        val p = st.egrPct?.toInt()?.let { "$it por ciento" } ?: "alto"
        return when (st.band) {
            "alert" -> "Atención. EGR real crítico. $p."
            "warn" -> "Cuidado. EGR real alto. $p."
            else -> "EGR real a $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("egr_pct" to st.egrPct?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
