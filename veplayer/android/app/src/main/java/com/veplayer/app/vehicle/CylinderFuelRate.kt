package com.veplayer.app.vehicle

/** Cylinder fuel rate mg/stroke (OBD PID 01A2 bytes A/B). */
object CylinderFuelRate {
    data class State(val mgPerStroke: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(mgPerStroke: Float?, speedKmh: Float = 0f, warnMg: Float = 40f, alertMg: Float = 55f, speedMinKmh: Float = 20f): State {
        if (mgPerStroke == null) return State(band = "idle", label = "")
        val mg = mgPerStroke.coerceIn(0f, 2048f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnMg.coerceIn(10f, 150f)
        val alert = alertMg.coerceAtLeast(warn + 5f).coerceAtMost(2048f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(mgPerStroke = mg, speedKmh = speed, band = "ok", label = if (mg >= warn - 5f) "CylFuel · ${mg.toInt()}" else "")
        }
        val band = when { mg >= alert -> "alert"; mg >= warn -> "warn"; else -> "ok" }
        return State(mgPerStroke = mg, speedKmh = speed, band = band, showWarn = band != "ok", label = "CylFuel · ${mg.toInt()}")
    }

    fun voicePhrase(st: State): String {
        val m = st.mgPerStroke?.toInt()?.let { "$it mg por ciclo" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Tasa cilindro crítica. $m."
            "warn" -> "Cuidado. Tasa cilindro alta. $m."
            else -> "Tasa cilindro a $m."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("mg_per_stroke" to st.mgPerStroke?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
