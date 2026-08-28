package com.veplayer.app.vehicle

/** EPCS diagnostic count (OBD PID 01C4 byte B). */
object EpcsDiagCount {
    data class State(val count: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(count: Float?, speedKmh: Float = 0f, warnCount: Float = 50f, alertCount: Float = 80f, speedMinKmh: Float = 20f): State {
        if (count == null) return State(band = "idle", label = "")
        val n = count.coerceIn(0f, 255f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnCount.coerceIn(10f, 200f)
        val alert = alertCount.coerceAtLeast(warn + 10f).coerceAtMost(255f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(count = n, speedKmh = speed, band = "ok", label = if (n >= warn - 10f) "EPCSn · ${n.toInt()}" else "")
        }
        val band = when { n >= alert -> "alert"; n >= warn -> "warn"; else -> "ok" }
        return State(count = n, speedKmh = speed, band = band, showWarn = band != "ok", label = "EPCSn · ${n.toInt()}")
    }

    fun voicePhrase(st: State): String {
        val c = st.count?.toInt()?.toString() ?: "alto"
        return when (st.band) {
            "alert" -> "Atención. Conteo EPCS crítico. $c."
            "warn" -> "Cuidado. Conteo EPCS alto. $c."
            else -> "Conteo EPCS a $c."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("count" to st.count?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
