package com.veplayer.app.vehicle

/** Max MAF capability g/s (OBD PID 0150). */
object MaxMafGps {
    data class State(val mafGps: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(mafGps: Float?, warnLowGps: Float = 25f, alertLowGps: Float = 15f): State {
        if (mafGps == null) return State(band = "idle", label = "")
        val g = mafGps.coerceAtLeast(0f)
        val warn = warnLowGps.coerceIn(5f, 100f)
        val alert = alertLowGps.coerceAtMost(warn - 5f).coerceAtLeast(5f)
        val band = when {
            g <= alert -> "alert"
            g <= warn -> "warn"
            else -> "ok"
        }
        return State(mafGps = g, band = band, showWarn = band != "ok", label = "MaxMAF · ${g.toInt()}g/s")
    }

    fun voicePhrase(st: State): String {
        val g = st.mafGps?.toInt()?.let { "$it gramos por segundo" } ?: "bajo"
        return when (st.band) {
            "alert" -> "Atención. MAF máximo crítico. $g. Revisa sensor."
            "warn" -> "Cuidado. MAF máximo bajo. $g."
            else -> "MAF máximo $g."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("maf_gps" to st.mafGps?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
