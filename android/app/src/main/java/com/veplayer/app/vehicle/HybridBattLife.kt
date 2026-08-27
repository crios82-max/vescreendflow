package com.veplayer.app.vehicle

/** Hybrid pack remaining life (OBD PID 015B), %. */
object HybridBattLife {
    data class State(val lifePct: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(lifePct: Float?, speedKmh: Float = 0f, warnPct: Float = 30f, alertPct: Float = 15f, speedMinKmh: Float = 0f): State {
        if (lifePct == null) return State(band = "idle", label = "")
        val pct = lifePct.coerceIn(0f, 100f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(10f, 60f)
        val alert = alertPct.coerceAtMost(warn - 5f).coerceAtLeast(5f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) return State(lifePct = pct, speedKmh = speed, band = "ok", label = if (pct <= 50f) "HyBatt · ${pct.toInt()}%" else "")
        val band = when { pct <= alert -> "alert"; pct <= warn -> "warn"; else -> "ok" }
        return State(lifePct = pct, speedKmh = speed, band = band, showWarn = band != "ok", label = "HyBatt · ${pct.toInt()}%")
    }

    fun voicePhrase(st: State): String {
        val p = st.lifePct?.toInt()?.let { "$it por ciento" } ?: "bajo"
        return when (st.band) {
            "alert" -> "Atención. Batería híbrida crítica. $p vida restante."
            "warn" -> "Cuidado. Batería híbrida baja. $p."
            else -> "Batería híbrida a $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("life_pct" to st.lifePct?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
