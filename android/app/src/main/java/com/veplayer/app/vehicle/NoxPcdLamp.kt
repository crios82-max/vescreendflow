package com.veplayer.app.vehicle

/** NOx/PCD diagnostic warning lamp (OBD PID 01C8). */
object NoxPcdLamp {
    data class State(val lampOn: Boolean = false, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(lampOn: Boolean, speedKmh: Float = 0f, speedMinKmh: Float = 20f): State {
        val speed = speedKmh.coerceAtLeast(0f)
        if (!lampOn) return State(lampOn = false, speedKmh = speed, band = "ok", label = "")
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(lampOn = true, speedKmh = speed, band = "ok", label = "NCD/PCD")
        }
        return State(lampOn = true, speedKmh = speed, band = "alert", showWarn = true, label = "NCD/PCD")
    }

    fun voicePhrase(st: State): String = "Atención. Lámpara diagnóstico NOx o partículas encendida."

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("lamp_on" to st.lampOn, "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
