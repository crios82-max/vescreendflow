package com.veplayer.app.vehicle

/** EPCS diagnostic time seconds (OBD PID 01C4 byte A). */
object EpcsDiagTime {
    data class State(val timeSec: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(timeSec: Float?, speedKmh: Float = 0f, warnSec: Float = 120f, alertSec: Float = 180f, speedMinKmh: Float = 20f): State {
        if (timeSec == null) return State(band = "idle", label = "")
        val sec = timeSec.coerceIn(0f, 255f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnSec.coerceIn(30f, 600f)
        val alert = alertSec.coerceAtLeast(warn + 30f).coerceAtMost(255f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(timeSec = sec, speedKmh = speed, band = "ok", label = if (sec >= warn - 20f) "EPCS · ${sec.toInt()}s" else "")
        }
        val band = when { sec >= alert -> "alert"; sec >= warn -> "warn"; else -> "ok" }
        return State(timeSec = sec, speedKmh = speed, band = band, showWarn = band != "ok", label = "EPCS · ${sec.toInt()}s")
    }

    fun voicePhrase(st: State): String {
        val s = st.timeSec?.toInt()?.let { "$it segundos" } ?: "alto"
        return when (st.band) {
            "alert" -> "Atención. Tiempo EPCS crítico. $s."
            "warn" -> "Cuidado. Tiempo EPCS alto. $s."
            else -> "Tiempo EPCS a $s."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("time_sec" to st.timeSec?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
