package com.veplayer.app.vehicle

/** HVESS discharge energy capacity kWh (OBD PID 01C2 bytes A/B /10). */
object HvDcap {
    data class State(val kwh: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(kwh: Float?, warnKwh: Float = 40f, alertKwh: Float = 25f): State {
        if (kwh == null) return State(band = "idle", label = "")
        val k = kwh.coerceIn(0f, 1e5f)
        val warn = warnKwh.coerceIn(5f, 500f)
        val alert = alertKwh.coerceAtMost(warn - 5f).coerceAtLeast(1f)
        val band = when { k <= alert -> "alert"; k <= warn -> "warn"; else -> "ok" }
        return State(kwh = k, band = band, showWarn = band != "ok", label = "HvDcap · ${"%.1f".format(k)}kWh")
    }

    fun voicePhrase(st: State): String {
        val k = st.kwh?.let { "${"%.1f".format(it)} kilovatios hora" } ?: "baja"
        return when (st.band) {
            "alert" -> "Atención. Capacidad descarga HV crítica. $k."
            "warn" -> "Cuidado. Capacidad descarga HV baja. $k."
            else -> "Capacidad descarga HV a $k."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("kwh" to st.kwh?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
