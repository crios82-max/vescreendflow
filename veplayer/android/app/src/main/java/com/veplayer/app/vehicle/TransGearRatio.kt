package com.veplayer.app.vehicle

/** Transmission actual gear ratio (OBD PID 01A4 bytes C/D). */
object TransGearRatio {
    data class State(val gearRatio: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(gearRatio: Float?, speedKmh: Float = 0f, warnRatio: Float = 2.5f, alertRatio: Float = 3.5f, speedMinKmh: Float = 20f): State {
        if (gearRatio == null) return State(band = "idle", label = "")
        val ratio = gearRatio.coerceIn(0f, 65.5f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnRatio.coerceIn(0.5f, 10f)
        val alert = alertRatio.coerceAtLeast(warn + 0.3f).coerceAtMost(65.5f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(gearRatio = ratio, speedKmh = speed, band = "ok", label = if (ratio >= warn - 0.2f) "Gear · ${fmt(ratio)}" else "")
        }
        val band = when { ratio >= alert -> "alert"; ratio >= warn -> "warn"; else -> "ok" }
        return State(gearRatio = ratio, speedKmh = speed, band = band, showWarn = band != "ok", label = "Gear · ${fmt(ratio)}")
    }

    private fun fmt(r: Float): String = String.format("%.2f", r)

    fun voicePhrase(st: State): String {
        val r = st.gearRatio?.let { fmt(it) } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Relación transmisión crítica. $r."
            "warn" -> "Cuidado. Relación transmisión alta. $r."
            else -> "Relación transmisión a $r."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("gear_ratio" to st.gearRatio?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
