package com.veplayer.app.vehicle

/** Fuel level input A % (OBD PID 01C3 byte A). */
object FuelLevelInputA {
    data class State(val levelPct: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(levelPct: Float?, speedKmh: Float = 0f, warnPct: Float = 15f, alertPct: Float = 8f, speedMinKmh: Float = 20f): State {
        if (levelPct == null) return State(band = "idle", label = "")
        val pct = levelPct.coerceIn(0f, 100f)
        val speed = speedKmh.coerceAtLeast(0f)
        val alert = alertPct.coerceIn(3f, 40f)
        val warn = warnPct.coerceAtLeast(alert + 3f).coerceAtMost(50f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(levelPct = pct, speedKmh = speed, band = "ok", label = if (pct <= warn + 5f) "FuelA · ${pct.toInt()}%" else "")
        }
        val band = when { pct <= alert -> "alert"; pct <= warn -> "warn"; else -> "ok" }
        return State(levelPct = pct, speedKmh = speed, band = band, showWarn = band != "ok", label = "FuelA · ${pct.toInt()}%")
    }

    fun voicePhrase(st: State): String {
        val p = st.levelPct?.toInt()?.let { "$it por ciento" } ?: "bajo"
        return when (st.band) {
            "alert" -> "Atención. Nivel combustible A crítico. $p."
            "warn" -> "Cuidado. Nivel combustible A bajo. $p."
            else -> "Nivel combustible A a $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("level_pct" to st.levelPct?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
