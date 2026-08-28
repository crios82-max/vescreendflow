package com.veplayer.app.vehicle

/** Engine exhaust flow kg/h (OBD PID 019E). */
object EngineExhaustFlow {
    data class State(val flowKgh: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(flowKgh: Float?, speedKmh: Float = 0f, warnKgh: Float = 35f, alertKgh: Float = 50f, speedMinKmh: Float = 20f): State {
        if (flowKgh == null) return State(band = "idle", label = "")
        val f = flowKgh.coerceIn(0f, 2000f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnKgh.coerceIn(5f, 500f)
        val alert = alertKgh.coerceAtLeast(warn + 5f).coerceAtMost(2000f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(flowKgh = f, speedKmh = speed, band = "ok", label = if (f >= warn - 5f) "ExhFlow · ${f.toInt()}" else "")
        }
        val band = when { f >= alert -> "alert"; f >= warn -> "warn"; else -> "ok" }
        return State(flowKgh = f, speedKmh = speed, band = band, showWarn = band != "ok", label = "ExhFlow · ${f.toInt()}")
    }

    fun voicePhrase(st: State): String {
        val v = st.flowKgh?.toInt()?.toString() ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Flujo exhaustivo crítico. $v kilogramos por hora."
            "warn" -> "Cuidado. Flujo exhaustivo alto. $v kilogramos por hora."
            else -> "Flujo exhaustivo a $v kilogramos por hora."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("flow_kgh" to st.flowKgh?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
