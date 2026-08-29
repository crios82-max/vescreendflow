package com.veplayer.app.vehicle

/** Remaining ESS reserve energy kWh (OBD PID 01D0 bytes A/B /10). */
object EssRsrv {
    data class State(val kwh: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(kwh: Float?, warnKwh: Float = 8f, alertKwh: Float = 3f): State {
        if (kwh == null) return State(band = "idle", label = "")
        val k = kwh.coerceIn(0f, 1e5f)
        val warn = warnKwh.coerceIn(1f, 200f)
        val alert = alertKwh.coerceAtMost(warn - 1f).coerceAtLeast(0.5f)
        val band = when { k <= alert -> "alert"; k <= warn -> "warn"; else -> "ok" }
        return State(kwh = k, band = band, showWarn = band != "ok", label = "EssRsrv · ${"%.1f".format(k)}kWh")
    }

    fun voicePhrase(st: State): String {
        val k = st.kwh?.let { "${"%.1f".format(it)} kilovatios hora" } ?: "baja"
        return when (st.band) {
            "alert" -> "Atención. Reserva ESS crítica. $k."
            "warn" -> "Cuidado. Reserva ESS baja. $k."
            else -> "Reserva ESS a $k."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("kwh" to st.kwh?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
