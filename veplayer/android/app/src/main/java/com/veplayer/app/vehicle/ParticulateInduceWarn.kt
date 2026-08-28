package com.veplayer.app.vehicle

/** Particulate driver inducement warning (OBD PID 01C6 byte A == 1). */
object ParticulateInduceWarn {
    data class State(val status: Int? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(status: Int?, speedKmh: Float = 0f, warnStatus: Int = 1, speedMinKmh: Float = 20f): State {
        if (status == null) return State(band = "idle", label = "")
        val s = status.coerceIn(0, 255)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnStatus.coerceIn(1, 10)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(status = s, speedKmh = speed, band = "ok", label = if (s == warn) "IndW · $s" else "")
        }
        val band = if (s == warn) "warn" else "ok"
        return State(status = s, speedKmh = speed, band = band, showWarn = band != "ok", label = if (band != "ok") "IndW · $s" else "")
    }

    fun voicePhrase(st: State): String = "Cuidado. Inducement partículas en aviso. Estado ${st.status ?: 0}."

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("status" to st.status, "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
