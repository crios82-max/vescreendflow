package com.veplayer.app.vehicle

/** Hybrid/EV pack voltage V (OBD PID 019A SAE C/D /64; legacy short A/B /10). */
object HybridEvBattVoltage {
    data class State(val volts: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(volts: Float?, warnV: Float = 280f, alertV: Float = 260f): State {
        if (volts == null) return State(band = "idle", label = "")
        val v = volts.coerceIn(0f, 1000f)
        val warn = warnV.coerceIn(200f, 400f)
        val alert = alertV.coerceIn(150f, warn - 5f)
        val band = when { v < alert -> "alert"; v < warn -> "warn"; else -> "ok" }
        return State(volts = v, band = band, showWarn = band != "ok", label = "HevV · ${v.toInt()}V")
    }

    fun voicePhrase(st: State): String {
        val v = st.volts?.toInt()?.let { "$it voltios" } ?: "baja"
        return when (st.band) {
            "alert" -> "Atención. Voltaje batería híbrida crítico. $v."
            "warn" -> "Cuidado. Voltaje batería híbrida bajo. $v."
            else -> "Batería híbrida a $v."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("volts" to st.volts?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
