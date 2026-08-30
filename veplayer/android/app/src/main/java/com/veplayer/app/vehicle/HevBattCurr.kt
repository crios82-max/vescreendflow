package com.veplayer.app.vehicle

/** Hybrid/EV battery current A (OBD PID 019A bytes E/F signed /10). High |A| = stress. */
object HevBattCurr {
    data class State(val amps: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(amps: Float?, warnA: Float = 150f, alertA: Float = 250f): State {
        if (amps == null) return State(band = "idle", label = "")
        val a = amps.coerceIn(-4000f, 4000f)
        val warn = warnA.coerceIn(20f, 1000f)
        val alert = alertA.coerceAtLeast(warn + 10f)
        val abs = kotlin.math.abs(a)
        val band = when {
            abs >= alert -> "alert"
            abs >= warn -> "warn"
            else -> "ok"
        }
        return State(amps = a, band = band, showWarn = band != "ok", label = "HevA · ${"%.0f".format(a)}A")
    }

    fun voicePhrase(st: State): String {
        val a = st.amps?.let { "${"%.0f".format(it)} amperios" } ?: "elevada"
        return when (st.band) {
            "alert" -> "Atención. Corriente batería híbrida crítica. $a."
            "warn" -> "Cuidado. Corriente batería híbrida alta. $a."
            else -> "Corriente batería híbrida a $a."
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
        mapOf("amps" to st.amps?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
