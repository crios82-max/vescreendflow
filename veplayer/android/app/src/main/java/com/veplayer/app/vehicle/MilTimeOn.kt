package com.veplayer.app.vehicle

/** Time run with MIL on (OBD PID 0154), minutes. */
object MilTimeOn {
    data class State(val minutes: Int? = null, val milOn: Boolean = false, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(minutes: Int?, milOn: Boolean = false, warnMin: Int = 30, alertMin: Int = 60): State {
        if (minutes == null || !milOn) {
            return State(minutes = minutes, milOn = milOn, band = if (minutes == null) "idle" else "ok",
                label = if (milOn && minutes != null) "MILt · ${minutes}m" else "")
        }
        val m = minutes.coerceAtLeast(0)
        val warn = warnMin.coerceAtLeast(5)
        val alert = alertMin.coerceAtLeast(warn + 5)
        val band = when { m >= alert -> "alert"; m >= warn -> "warn"; else -> "ok" }
        return State(minutes = m, milOn = milOn, band = band, showWarn = band != "ok", label = "MILt · ${m}m")
    }

    fun voicePhrase(st: State): String {
        val m = st.minutes?.let { "$it minutos" } ?: "mucho tiempo"
        return when (st.band) {
            "alert" -> "Atención. Llevas $m con MIL encendida. Revisa el motor."
            "warn" -> "Cuidado. $m con luz de motor activa."
            else -> "Tiempo con MIL: $m."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF59E0B; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("minutes" to st.minutes, "mil_on" to st.milOn, "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
