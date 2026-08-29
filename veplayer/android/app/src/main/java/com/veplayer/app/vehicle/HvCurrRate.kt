package com.veplayer.app.vehicle

/** Battery pack current rate Ah/s (OBD PID 01DA bytes A/B signed /100). High |rate| = stress. */
object HvCurrRate {
    data class State(val ahs: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(ahs: Float?, warnAhs: Float = 15f, alertAhs: Float = 30f): State {
        if (ahs == null) return State(band = "idle", label = "")
        val a = kotlin.math.abs(ahs).coerceIn(0f, 1e4f)
        val warn = warnAhs.coerceIn(2f, 200f)
        val alert = alertAhs.coerceAtLeast(warn + 2f)
        val band = when { a >= alert -> "alert"; a >= warn -> "warn"; else -> "ok" }
        return State(ahs = ahs, band = band, showWarn = band != "ok", label = "HvCurr · ${"%.2f".format(ahs)}Ah/s")
    }

    fun voicePhrase(st: State): String {
        val a = st.ahs?.let { "${"%.2f".format(it)} amperios hora por segundo" } ?: "elevada"
        return when (st.band) {
            "alert" -> "Atención. Tasa de corriente HV crítica. $a."
            "warn" -> "Cuidado. Tasa de corriente HV alta. $a."
            else -> "Tasa de corriente HV a $a."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("ahs" to st.ahs?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
