package com.veplayer.app.vehicle

/** Fuel system closed-loop control count (OBD PID 0192 byte B popcount). */
object FuelSysCtlClosed {
    data class State(val closedCount: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(closedCount: Float?, speedKmh: Float = 0f, warnMin: Float = 3f, alertMin: Float = 2f, speedMinKmh: Float = 20f): State {
        if (closedCount == null) return State(band = "idle", label = "")
        val c = closedCount.coerceIn(0f, 8f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnMin.coerceIn(1f, 7f)
        val alert = alertMin.coerceIn(0f, warn - 1f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(closedCount = c, speedKmh = speed, band = "ok", label = if (c <= alert + 1f) "FSCctl · ${c.toInt()}" else "")
        }
        val band = when { c < alert -> "alert"; c < warn -> "warn"; else -> "ok" }
        return State(closedCount = c, speedKmh = speed, band = band, showWarn = band != "ok", label = "FSCctl · ${c.toInt()}")
    }

    fun voicePhrase(st: State): String {
        val c = st.closedCount?.toInt()?.toString() ?: "bajo"
        return when (st.band) {
            "alert" -> "Atención. Control combustible en lazo abierto. $c controles cerrados."
            "warn" -> "Cuidado. Pocos controles combustible en lazo cerrado. $c."
            else -> "Control combustible: $c en lazo cerrado."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("closed_count" to st.closedCount?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
