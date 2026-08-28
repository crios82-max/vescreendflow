package com.veplayer.app.vehicle

/** Relative accel pedal (OBD PID 015A), %. */
object RelAccelPedal {
    data class State(val pedalPct: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(pedalPct: Float?, speedKmh: Float = 0f, warnPct: Float = 78f, alertPct: Float = 90f, speedMinKmh: Float = 20f): State {
        if (pedalPct == null) return State(band = "idle", label = "")
        val p = pedalPct.coerceIn(0f, 100f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(50f, 95f)
        val alert = alertPct.coerceAtLeast(warn + 3f).coerceAtMost(100f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) return State(pedalPct = p, speedKmh = speed, band = "ok", label = if (p >= 45f) "RelAP · ${p.toInt()}%" else "")
        val band = when { p >= alert -> "alert"; p >= warn -> "warn"; else -> "ok" }
        return State(pedalPct = p, speedKmh = speed, band = band, showWarn = band != "ok", label = "RelAP · ${p.toInt()}%")
    }

    fun voicePhrase(st: State): String {
        val p = st.pedalPct?.toInt()?.let { "$it por ciento" } ?: "alto"
        return when (st.band) {
            "alert" -> "Atención. Pedal relativo crítico. $p."; "warn" -> "Cuidado. Pedal relativo alto. $p."; else -> "Pedal relativo a $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("pedal_pct" to st.pedalPct?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
