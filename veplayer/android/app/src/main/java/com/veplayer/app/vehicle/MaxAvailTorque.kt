package com.veplayer.app.vehicle

/** Max available engine torque % (OBD PID 0164), signed offset 125. */
object MaxAvailTorque {
    data class State(val torquePct: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(torquePct: Float?, speedKmh: Float = 0f, warnLowPct: Float = 30f, alertLowPct: Float = 20f, speedMinKmh: Float = 10f): State {
        if (torquePct == null) return State(band = "idle", label = "")
        val t = torquePct.coerceIn(-125f, 125f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnLowPct.coerceIn(10f, 80f)
        val alert = alertLowPct.coerceAtMost(warn - 5f).coerceAtLeast(5f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(torquePct = t, speedKmh = speed, band = "ok", label = if (t < 50f) "MaxTq · ${t.toInt()}%" else "")
        }
        val band = when { t <= alert -> "alert"; t <= warn -> "warn"; else -> "ok" }
        return State(torquePct = t, speedKmh = speed, band = band, showWarn = band != "ok", label = "MaxTq · ${t.toInt()}%")
    }

    fun voicePhrase(st: State): String {
        val p = st.torquePct?.toInt()?.let { "$it por ciento" } ?: "bajo"
        return when (st.band) {
            "alert" -> "Atención. Torque máximo disponible crítico. $p."
            "warn" -> "Cuidado. Torque máximo bajo. $p."
            else -> "Torque máximo $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("torque_pct" to st.torquePct?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
