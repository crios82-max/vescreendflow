package com.veplayer.app.vehicle

/** Max equivalence ratio capability (OBD PID 014F). */
object MaxEquivRatio {
    data class State(val ratio: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(ratio: Float?, warnLow: Float = 0.88f, alertLow: Float = 0.82f, warnHigh: Float = 1.18f, alertHigh: Float = 1.24f): State {
        if (ratio == null) return State(band = "idle", label = "")
        val r = ratio.coerceIn(0.1f, 2.5f)
        val band = when {
            r <= alertLow || r >= alertHigh -> "alert"
            r <= warnLow || r >= warnHigh -> "warn"
            else -> "ok"
        }
        return State(ratio = r, band = band, showWarn = band != "ok", label = "Maxλ · ${fmt(r)}")
    }

    private fun fmt(r: Float): String = String.format("%.2f", r)

    fun voicePhrase(st: State): String {
        val v = st.ratio?.let { fmt(it) } ?: "anómalo"
        return when (st.band) {
            "alert" -> "Atención. Lambda máxima crítica. $v."
            "warn" -> "Cuidado. Lambda máxima fuera de rango. $v."
            else -> "Lambda máxima $v."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("ratio" to st.ratio?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
