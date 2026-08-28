package com.veplayer.app.vehicle

/** Fuel pressure control kPa (OBD PID 016D). */
object FuelPressureControl {
    data class State(val pressureKpa: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(pressureKpa: Float?, speedKmh: Float = 0f, warnKpa: Float = 6000f, alertKpa: Float = 9000f, speedMinKmh: Float = 10f): State {
        if (pressureKpa == null) return State(band = "idle", label = "")
        val p = pressureKpa.coerceAtLeast(0f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnKpa.coerceIn(1500f, 15000f)
        val alert = alertKpa.coerceAtLeast(warn + 400f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(pressureKpa = p, speedKmh = speed, band = "ok", label = if (p >= 3000f) "FuelCtrl · ${p.toInt()}kPa" else "")
        }
        val band = when { p >= alert -> "alert"; p >= warn -> "warn"; else -> "ok" }
        return State(pressureKpa = p, speedKmh = speed, band = band, showWarn = band != "ok", label = "FuelCtrl · ${p.toInt()}kPa")
    }

    fun voicePhrase(st: State): String {
        val k = st.pressureKpa?.toInt()?.let { "$it kilopascales" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Control combustible crítico. $k."
            "warn" -> "Cuidado. Control combustible alto. $k."
            else -> "Control combustible a $k."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("pressure_kpa" to st.pressureKpa?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
