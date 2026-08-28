package com.veplayer.app.vehicle

/** Commanded throttle actuator % (OBD PID 016C byte A). */
object ThrottleActuator {
    data class State(val actuatorPct: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(actuatorPct: Float?, speedKmh: Float = 0f, warnPct: Float = 85f, alertPct: Float = 92f, speedMinKmh: Float = 10f): State {
        if (actuatorPct == null) return State(band = "idle", label = "")
        val p = actuatorPct.coerceIn(0f, 100f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(50f, 98f)
        val alert = alertPct.coerceAtLeast(warn + 2f).coerceAtMost(100f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(actuatorPct = p, speedKmh = speed, band = "ok", label = if (p >= 60f) "ThrAct · ${p.toInt()}%" else "")
        }
        val band = when { p >= alert -> "alert"; p >= warn -> "warn"; else -> "ok" }
        return State(actuatorPct = p, speedKmh = speed, band = band, showWarn = band != "ok", label = "ThrAct · ${p.toInt()}%")
    }

    fun voicePhrase(st: State): String {
        val p = st.actuatorPct?.toInt()?.let { "$it por ciento" } ?: "alto"
        return when (st.band) {
            "alert" -> "Atención. Actuador mariposa crítico. $p."
            "warn" -> "Cuidado. Actuador mariposa alto. $p."
            else -> "Actuador mariposa a $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("actuator_pct" to st.actuatorPct?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
