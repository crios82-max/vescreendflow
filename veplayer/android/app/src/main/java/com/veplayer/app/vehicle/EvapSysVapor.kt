package com.veplayer.app.vehicle

/** Evap system vapor pressure Pa (OBD PID 01A3 bytes B/C signed). */
object EvapSysVapor {
    data class State(val pressurePa: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(pressurePa: Float?, speedKmh: Float = 0f, warnAbsPa: Float = 5000f, alertAbsPa: Float = 8000f, speedMinKmh: Float = 20f): State {
        if (pressurePa == null) return State(band = "idle", label = "")
        val pa = pressurePa.coerceIn(-32000f, 32000f)
        val abs = kotlin.math.abs(pa)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnAbsPa.coerceIn(500f, 20000f)
        val alert = alertAbsPa.coerceAtLeast(warn + 500f).coerceAtMost(32000f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(pressurePa = pa, speedKmh = speed, band = "ok", label = if (abs >= warn - 500f) "EvapVP · ${pa.toInt()}" else "")
        }
        val band = when { abs >= alert -> "alert"; abs >= warn -> "warn"; else -> "ok" }
        return State(pressurePa = pa, speedKmh = speed, band = band, showWarn = band != "ok", label = "EvapVP · ${pa.toInt()}")
    }

    fun voicePhrase(st: State): String {
        val p = st.pressurePa?.toInt()?.let { "$it Pa" } ?: "elevado"
        return when (st.band) {
            "alert" -> "Atención. Vapor evaporativo sistema crítico. $p."
            "warn" -> "Cuidado. Vapor evaporativo sistema alto. $p."
            else -> "Vapor evaporativo sistema a $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("pressure_pa" to st.pressurePa?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
