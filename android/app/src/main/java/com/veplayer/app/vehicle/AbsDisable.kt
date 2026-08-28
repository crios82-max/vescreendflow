package com.veplayer.app.vehicle

/** ABS disable switch state (OBD PID 01A9). */
object AbsDisable {
    data class State(val disabled: Boolean = false, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(supported: Boolean, disabled: Boolean, speedKmh: Float = 0f, speedMinKmh: Float = 20f): State {
        if (!supported) return State(band = "idle", label = "")
        val speed = speedKmh.coerceAtLeast(0f)
        if (!disabled) return State(disabled = false, speedKmh = speed, band = "ok", label = "")
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(disabled = true, speedKmh = speed, band = "ok", label = "ABS off")
        }
        return State(disabled = true, speedKmh = speed, band = "alert", showWarn = true, label = "ABS off")
    }

    fun voicePhrase(st: State): String = "Atención. ABS desactivado mientras conduces."

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("disabled" to st.disabled, "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
