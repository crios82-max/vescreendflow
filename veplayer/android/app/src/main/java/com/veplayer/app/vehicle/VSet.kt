package com.veplayer.app.vehicle

/** Max vehicle speed limit km/h (OBD PID 01AA byte A). Low limit = limiter/limp active. */
object VSet {
    data class State(val kmh: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(kmh: Float?, warnKmh: Float = 100f, alertKmh: Float = 60f): State {
        if (kmh == null) return State(band = "idle", label = "")
        val v = kmh.coerceIn(0f, 255f)
        if (v <= 0f) return State(kmh = v, band = "idle", label = "")
        val warn = warnKmh.coerceIn(30f, 200f)
        val alert = alertKmh.coerceAtMost(warn - 5f).coerceAtLeast(10f)
        val band = when {
            v <= alert -> "alert"
            v <= warn -> "warn"
            else -> "ok"
        }
        return State(kmh = v, band = band, showWarn = band != "ok", label = "VSet · ${v.toInt()}km/h")
    }

    fun voicePhrase(st: State): String {
        val v = st.kmh?.toInt()?.let { "$it kilómetros por hora" } ?: "bajo"
        return when (st.band) {
            "alert" -> "Atención. Límite de velocidad crítico. $v."
            "warn" -> "Cuidado. Limitador de velocidad activo. $v."
            else -> "Límite de velocidad a $v."
        }
    }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFFE11D48
            "warn" -> 0xFFF97316
            "ok" -> 0xFF14B8A6
            else -> 0xFF94A3B8
        }

    fun toJsonMap(st: State): Map<String, Any?> =
        mapOf("kmh" to st.kmh?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
