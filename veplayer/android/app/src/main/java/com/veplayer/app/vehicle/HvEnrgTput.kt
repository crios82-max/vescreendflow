package com.veplayer.app.vehicle

/** HVESS total energy throughput Wh (OBD PID 01BD bytes A–D). */
object HvEnrgTput {
    data class State(val wh: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(wh: Float?, warnWh: Float = 3e7f, alertWh: Float = 5e7f): State {
        if (wh == null) return State(band = "idle", label = "")
        val w = wh.coerceIn(0f, 1e10f)
        val warn = warnWh.coerceIn(1e6f, 1e9f)
        val alert = alertWh.coerceAtLeast(warn + 1e6f)
        val band = when { w >= alert -> "alert"; w >= warn -> "warn"; else -> "ok" }
        val kwh = w / 1000f
        return State(wh = w, band = band, showWarn = band != "ok", label = "HvTput · ${kwh.toInt()}kWh")
    }

    fun voicePhrase(st: State): String {
        val k = st.wh?.let { "${(it / 1000f).toInt()} kWh" } ?: "elevado"
        return when (st.band) {
            "alert" -> "Atención. Throughput HV crítico. $k."
            "warn" -> "Cuidado. Throughput HV alto. $k."
            else -> "Throughput HV a $k."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("wh" to st.wh?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
