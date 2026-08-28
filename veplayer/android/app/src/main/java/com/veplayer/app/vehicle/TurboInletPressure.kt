package com.veplayer.app.vehicle

/** Turbocharger compressor inlet pressure kPa (OBD PID 016F byte A). */
object TurboInletPressure {
    data class State(val pressureKpa: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(pressureKpa: Float?, speedKmh: Float = 0f, warnKpa: Float = 200f, alertKpa: Float = 230f, speedMinKmh: Float = 15f): State {
        if (pressureKpa == null) return State(band = "idle", label = "")
        val p = pressureKpa.coerceIn(0f, 255f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnKpa.coerceIn(120f, 250f)
        val alert = alertKpa.coerceAtLeast(warn + 5f).coerceAtMost(255f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(pressureKpa = p, speedKmh = speed, band = "ok", label = if (p >= 150f) "TurboIn · ${p.toInt()}kPa" else "")
        }
        val band = when { p >= alert -> "alert"; p >= warn -> "warn"; else -> "ok" }
        return State(pressureKpa = p, speedKmh = speed, band = band, showWarn = band != "ok", label = "TurboIn · ${p.toInt()}kPa")
    }

    fun voicePhrase(st: State): String {
        val k = st.pressureKpa?.toInt()?.let { "$it kilopascales" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Presión turbo inlet crítica. $k."
            "warn" -> "Cuidado. Presión turbo inlet alta. $k."
            else -> "Turbo inlet a $k."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("pressure_kpa" to st.pressureKpa?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
