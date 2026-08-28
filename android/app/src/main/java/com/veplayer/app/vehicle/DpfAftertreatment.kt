package com.veplayer.app.vehicle

/** DPF aftertreatment trigger % (OBD PID 018B byte C). */
object DpfAftertreatment {
    data class State(val triggerPct: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(triggerPct: Float?, speedKmh: Float = 0f, warnPct: Float = 70f, alertPct: Float = 85f, speedMinKmh: Float = 15f): State {
        if (triggerPct == null) return State(band = "idle", label = "")
        val p = triggerPct.coerceIn(0f, 100f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(40f, 95f)
        val alert = alertPct.coerceAtLeast(warn + 5f).coerceAtMost(100f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(triggerPct = p, speedKmh = speed, band = "ok", label = if (p >= 50f) "DpfTrig · ${p.toInt()}%" else "")
        }
        val band = when { p >= alert -> "alert"; p >= warn -> "warn"; else -> "ok" }
        return State(triggerPct = p, speedKmh = speed, band = band, showWarn = band != "ok", label = "DpfTrig · ${p.toInt()}%")
    }

    fun voicePhrase(st: State): String {
        val p = st.triggerPct?.toInt()?.let { "$it por ciento" } ?: "alto"
        return when (st.band) {
            "alert" -> "Atención. DPF requiere regeneración crítica. $p."
            "warn" -> "Cuidado. DPF cerca de regeneración. $p."
            else -> "DPF trigger a $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("trigger_pct" to st.triggerPct?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
