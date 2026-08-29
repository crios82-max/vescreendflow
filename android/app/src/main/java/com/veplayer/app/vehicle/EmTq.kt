package com.veplayer.app.vehicle

/** Electric motor A torque Nm (OBD PID 01CD bytes A/B signed /10). High |Nm| = stress. */
object EmTq {
    data class State(val nm: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(nm: Float?, warnNm: Float = 250f, alertNm: Float = 400f): State {
        if (nm == null) return State(band = "idle", label = "")
        val n = kotlin.math.abs(nm).coerceIn(0f, 5000f)
        val warn = warnNm.coerceIn(50f, 2000f)
        val alert = alertNm.coerceAtLeast(warn + 20f)
        val band = when { n >= alert -> "alert"; n >= warn -> "warn"; else -> "ok" }
        return State(nm = nm, band = band, showWarn = band != "ok", label = "EmTq · ${"%.0f".format(nm)}Nm")
    }

    fun voicePhrase(st: State): String {
        val n = st.nm?.let { "${"%.0f".format(it)} newton metro" } ?: "elevado"
        return when (st.band) {
            "alert" -> "Atención. Par motor eléctrico crítico. $n."
            "warn" -> "Cuidado. Par motor eléctrico alto. $n."
            else -> "Par motor eléctrico a $n."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("nm" to st.nm?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
