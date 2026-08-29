package com.veplayer.app.vehicle

/** ESS charging limit kW (OBD PID 01D1 bytes A/B signed /10). Low = restricted charge. */
object EssChgLim {
    data class State(val kw: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(kw: Float?, warnKw: Float = 20f, alertKw: Float = 8f): State {
        if (kw == null) return State(band = "idle", label = "")
        val k = kw.coerceIn(0f, 500f)
        val warn = warnKw.coerceIn(5f, 200f)
        val alert = alertKw.coerceAtMost(warn - 2f).coerceAtLeast(1f)
        val band = when { k <= alert -> "alert"; k <= warn -> "warn"; else -> "ok" }
        return State(kw = k, band = band, showWarn = band != "ok", label = "EssLim · ${"%.1f".format(k)}kW")
    }

    fun voicePhrase(st: State): String {
        val k = st.kw?.let { "${"%.1f".format(it)} kilovatios" } ?: "bajo"
        return when (st.band) {
            "alert" -> "Atención. Límite de carga ESS crítico. $k."
            "warn" -> "Cuidado. Límite de carga ESS bajo. $k."
            else -> "Límite de carga ESS a $k."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("kw" to st.kw?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
