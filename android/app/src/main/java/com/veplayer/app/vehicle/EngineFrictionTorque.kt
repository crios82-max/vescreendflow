package com.veplayer.app.vehicle

/** Engine friction percent torque (OBD PID 018E), signed. */
object EngineFrictionTorque {
    data class State(val frictionPct: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(frictionPct: Float?, speedKmh: Float = 0f, warnPct: Float = 35f, alertPct: Float = 50f, speedMinKmh: Float = 20f): State {
        if (frictionPct == null) return State(band = "idle", label = "")
        val f = frictionPct.coerceIn(-125f, 125f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(10f, 100f)
        val alert = alertPct.coerceAtLeast(warn + 5f).coerceAtMost(125f)
        val abs = kotlin.math.abs(f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(frictionPct = f, speedKmh = speed, band = "ok", label = if (abs >= 20f) "Frict · ${f.toInt()}%" else "")
        }
        val band = when { abs >= alert -> "alert"; abs >= warn -> "warn"; else -> "ok" }
        return State(frictionPct = f, speedKmh = speed, band = band, showWarn = band != "ok", label = "Frict · ${f.toInt()}%")
    }

    fun voicePhrase(st: State): String {
        val p = st.frictionPct?.toInt()?.let { "$it por ciento" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Fricción motor crítica. $p."
            "warn" -> "Cuidado. Fricción motor alta. $p."
            else -> "Fricción motor a $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("friction_pct" to st.frictionPct?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
