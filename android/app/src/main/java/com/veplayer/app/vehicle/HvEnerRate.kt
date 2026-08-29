package com.veplayer.app.vehicle

/** Battery pack energy rate Wh/s (OBD PID 01D4 bytes A/B signed /10). High |rate| = stress. */
object HvEnerRate {
    data class State(val whs: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(whs: Float?, warnWhs: Float = 40f, alertWhs: Float = 70f): State {
        if (whs == null) return State(band = "idle", label = "")
        val w = kotlin.math.abs(whs).coerceIn(0f, 1e5f)
        val warn = warnWhs.coerceIn(5f, 500f)
        val alert = alertWhs.coerceAtLeast(warn + 5f)
        val band = when { w >= alert -> "alert"; w >= warn -> "warn"; else -> "ok" }
        return State(whs = whs, band = band, showWarn = band != "ok", label = "HvEner · ${"%.1f".format(whs)}Wh/s")
    }

    fun voicePhrase(st: State): String {
        val w = st.whs?.let { "${"%.1f".format(it)} vatios hora por segundo" } ?: "elevada"
        return when (st.band) {
            "alert" -> "Atención. Tasa de energía HV crítica. $w."
            "warn" -> "Cuidado. Tasa de energía HV alta. $w."
            else -> "Tasa de energía HV a $w."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("whs" to st.whs?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
