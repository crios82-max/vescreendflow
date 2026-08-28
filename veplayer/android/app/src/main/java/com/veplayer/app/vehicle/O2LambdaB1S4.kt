package com.veplayer.app.vehicle

/** O2 wide-range lambda bank 1 sensor 4 (OBD PID 019C bytes L/M). */
object O2LambdaB1S4 {
    data class State(val lambda: Float? = null, val speedKmh: Float = 0f, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(lambda: Float?, speedKmh: Float = 0f, warnLambda: Float = 1.10f, alertLambda: Float = 1.15f, speedMinKmh: Float = 20f): State {
        if (lambda == null) return State(band = "idle", label = "")
        val l = lambda.coerceIn(0.5f, 2f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnLambda.coerceIn(0.9f, 1.5f)
        val alert = alertLambda.coerceAtLeast(warn + 0.02f).coerceAtMost(2f)
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(lambda = l, speedKmh = speed, band = "ok", label = if (l >= 1.02f) "O2λ4 · ${fmt(l)}" else "")
        }
        val band = when { l >= alert -> "alert"; l >= warn -> "warn"; else -> "ok" }
        return State(lambda = l, speedKmh = speed, band = band, showWarn = band != "ok", label = "O2λ4 · ${fmt(l)}")
    }

    private fun fmt(l: Float): String = String.format("%.2f", l)

    fun voicePhrase(st: State): String {
        val v = st.lambda?.let { fmt(it) } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Lambda O2 B1S4 crítica. $v."
            "warn" -> "Cuidado. Lambda O2 B1S4 alta. $v."
            else -> "Lambda O2 B1S4 a $v."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("lambda" to st.lambda?.toDouble(), "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
