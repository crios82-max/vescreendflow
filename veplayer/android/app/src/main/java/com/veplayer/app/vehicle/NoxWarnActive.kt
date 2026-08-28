package com.veplayer.app.vehicle

/** NOx warning system active (OBD PID 0194 byte B bit0). */
object NoxWarnActive {
    data class State(val active: Boolean = false, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(active: Boolean, speedKmh: Float = 0f, speedMinKmh: Float = 20f): State {
        val speed = speedKmh.coerceAtLeast(0f)
        if (!active) return State(active = false, speedKmh = speed, band = "ok", label = "")
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(active = true, speedKmh = speed, band = "ok", label = "NOxWarn")
        }
        return State(active = true, speedKmh = speed, band = "alert", showWarn = true, label = "NOxWarn")
    }

    fun voicePhrase(st: State): String = "Atención. Sistema de aviso NOx activo."

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("active" to st.active, "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
