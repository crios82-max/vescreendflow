package com.veplayer.app.vehicle

/** NOx concentration sensor 4 ppm (OBD PID 01A7 bytes C/D). */
object NoxConcS4 {
    data class State(val noxPpm: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(noxPpm: Float?, speedKmh: Float = 0f, warnPpm: Float = 600f, alertPpm: Float = 800f, speedMinKmh: Float = 20f): State {
        if (noxPpm == null) return State(band = "idle", label = "")
        val ppm = noxPpm.coerceIn(0f, 5000f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPpm.coerceIn(100f, 2000f)
        val alert = alertPpm.coerceAtLeast(warn + 50f).coerceAtMost(5000f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(noxPpm = ppm, speedKmh = speed, band = "ok", label = if (ppm >= warn - 50f) "NOx4 · ${ppm.toInt()}" else "")
        }
        val band = when { ppm >= alert -> "alert"; ppm >= warn -> "warn"; else -> "ok" }
        return State(noxPpm = ppm, speedKmh = speed, band = band, showWarn = band != "ok", label = "NOx4 · ${ppm.toInt()}")
    }

    fun voicePhrase(st: State): String {
        val p = st.noxPpm?.toInt()?.let { "$it ppm" } ?: "elevado"
        return when (st.band) {
            "alert" -> "Atención. NOx concentración S4 crítica. $p."
            "warn" -> "Cuidado. NOx concentración S4 alta. $p."
            else -> "NOx concentración S4 a $p."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("nox_ppm" to st.noxPpm?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
