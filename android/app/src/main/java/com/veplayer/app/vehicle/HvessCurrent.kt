package com.veplayer.app.vehicle

/** HVESS current A (OBD PID 01B5 bytes A/B signed). */
object HvessCurrent {
    data class State(val currentA: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(currentA: Float?, speedKmh: Float = 0f, warnA: Float = 120f, alertA: Float = 180f, speedMinKmh: Float = 10f): State {
        if (currentA == null) return State(band = "idle", label = "")
        val a = currentA.coerceIn(-500f, 500f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnA.coerceIn(20f, 400f)
        val alert = alertA.coerceAtLeast(warn + 10f)
        val absA = kotlin.math.abs(a)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(currentA = a, speedKmh = speed, band = "ok", label = if (absA >= warn - 10f) "HvCur · ${a.toInt()}A" else "")
        }
        val band = when { absA >= alert -> "alert"; absA >= warn -> "warn"; else -> "ok" }
        return State(currentA = a, speedKmh = speed, band = band, showWarn = band != "ok", label = "HvCur · ${a.toInt()}A")
    }

    fun voicePhrase(st: State): String {
        val a = st.currentA?.toInt()?.let { "$it amperios" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Corriente HVESS crítica. $a."
            "warn" -> "Cuidado. Corriente HVESS alta. $a."
            else -> "Corriente HVESS a $a."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("current_a" to st.currentA?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
