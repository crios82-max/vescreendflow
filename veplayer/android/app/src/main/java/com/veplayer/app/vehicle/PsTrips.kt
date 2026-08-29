package com.veplayer.app.vehicle

/** Propulsion system active trips since fault memory clear (OBD PID 01D6 bytes A/B). High = many cycles. */
object PsTrips {
    data class State(val trips: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(trips: Float?, warnN: Float = 800f, alertN: Float = 1500f): State {
        if (trips == null) return State(band = "idle", label = "")
        val t = trips.coerceIn(0f, 65535f)
        val warn = warnN.coerceIn(50f, 30000f)
        val alert = alertN.coerceAtLeast(warn + 50f)
        val band = when { t >= alert -> "alert"; t >= warn -> "warn"; else -> "ok" }
        return State(trips = t, band = band, showWarn = band != "ok", label = "PsTrips · ${t.toInt()}")
    }

    fun voicePhrase(st: State): String {
        val t = st.trips?.toInt()?.toString() ?: "elevado"
        return when (st.band) {
            "alert" -> "Atención. Muchos ciclos de propulsión desde borrado de fallas. $t."
            "warn" -> "Cuidado. Ciclos de propulsión altos desde borrado. $t."
            else -> "Ciclos de propulsión: $t."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("trips" to st.trips?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
