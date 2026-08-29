package com.veplayer.app.vehicle

/** NOx level-one inducement status (OBD PID 0194 byte B bits 2–1). */
object NoxInduceLevel1 {
    data class State(val status: Int? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(status: Int?, speedKmh: Float = 0f, speedMinKmh: Float = 20f): State {
        if (status == null) return State(band = "idle", label = "")
        val s = status.coerceIn(0, 3)
        val speed = speedKmh.coerceAtLeast(0f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(status = s, speedKmh = speed, band = "ok", label = if (s >= 1) "IndL1 · $s" else "")
        }
        val band = when (s) { 2 -> "alert"; 1 -> "warn"; else -> "ok" }
        return State(status = s, speedKmh = speed, band = band, showWarn = band != "ok", label = if (band != "ok") "IndL1 · $s" else "")
    }

    fun voicePhrase(st: State): String = when (st.band) {
        "alert" -> "Atención. Inducement NOx nivel uno activo."
        "warn" -> "Cuidado. Inducement NOx nivel uno habilitado."
        else -> "Inducement NOx nivel uno estado ${st.status ?: 0}."
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("status" to st.status, "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
