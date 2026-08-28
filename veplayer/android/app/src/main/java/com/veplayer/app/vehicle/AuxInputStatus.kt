package com.veplayer.app.vehicle

/** Auxiliary input status bitmask (OBD PID 0165). */
object AuxInputStatus {
    data class State(val statusCode: Int? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(statusCode: Int?, speedKmh: Float = 0f, alertMask: Int = 0x0F, speedMinKmh: Float = 10f): State {
        if (statusCode == null || statusCode == 0) return State(band = "idle", label = "")
        val code = statusCode and 0xFF
        val speed = speedKmh.coerceAtLeast(0f)
        val mask = alertMask and 0xFF
        val label = "Aux · 0x${code.toString(16).uppercase().padStart(2, '0')}"
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(statusCode = code, speedKmh = speed, band = "ok", label = label)
        }
        val hit = code and mask
        val band = if (hit != 0) "alert" else "ok"
        return State(statusCode = code, speedKmh = speed, band = band, showWarn = band != "ok", label = label)
    }

    fun voicePhrase(st: State): String {
        val c = st.statusCode?.let { "código $it" } ?: "activo"
        return when (st.band) {
            "alert" -> "Atención. Entrada auxiliar anómala. $c."
            else -> "Auxiliar $c."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("status_code" to st.statusCode, "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
