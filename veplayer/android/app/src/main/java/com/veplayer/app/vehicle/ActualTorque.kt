package com.veplayer.app.vehicle

/** Actual engine torque % (OBD PID 0162), signed. */
object ActualTorque {
    data class State(val torquePct: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(torquePct: Float?, speedKmh: Float = 0f, warnPct: Float = 40f, alertPct: Float = 55f, speedMinKmh: Float = 20f): State {
        if (torquePct == null) return State(band = "idle", label = "")
        val t = torquePct.coerceIn(-125f, 125f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(15f, 100f)
        val alert = alertPct.coerceAtLeast(warn + 5f).coerceAtMost(125f)
        val abs = kotlin.math.abs(t)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) return State(torquePct = t, speedKmh = speed, band = "ok", label = if (abs >= 20f) "ActT · ${t.toInt()}%" else "")
        val band = when { abs >= alert -> "alert"; abs >= warn -> "warn"; else -> "ok" }
        return State(torquePct = t, speedKmh = speed, band = band, showWarn = band != "ok", label = "ActT · ${t.toInt()}%")
    }

    fun voicePhrase(st: State): String {
        val p = st.torquePct?.toInt()?.let { "$it por ciento" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Torque real crítico. $p."; "warn" -> "Cuidado. Torque real alto. $p."; else -> "Torque real a $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("torque_pct" to st.torquePct?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
