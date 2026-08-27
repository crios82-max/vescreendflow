package com.veplayer.app.vehicle

/** Relative throttle position (OBD PID 0145), %. */
object RelativeThrottle {
    data class State(
        val throttlePct: Float? = null,
        val speedKmh: Float = 0f,
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        throttlePct: Float?,
        speedKmh: Float = 0f,
        warnPct: Float = 75f,
        alertPct: Float = 90f,
        speedMinKmh: Float = 20f,
    ): State {
        if (throttlePct == null) return State(band = "idle", label = "")
        val thr = throttlePct.coerceIn(0f, 100f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(40f, 95f)
        val alert = alertPct.coerceAtLeast(warn + 5f).coerceAtMost(100f)
        val minSpd = speedMinKmh.coerceIn(0f, 60f)
        if (speed < minSpd) {
            return State(
                throttlePct = thr,
                speedKmh = speed,
                band = "ok",
                label = if (thr >= 45f) "RelT · ${thr.toInt()}%" else "",
            )
        }
        val band =
            when {
                thr >= alert -> "alert"
                thr >= warn -> "warn"
                else -> "ok"
            }
        return State(
            throttlePct = thr,
            speedKmh = speed,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "RelT · ${thr.toInt()}%",
        )
    }

    fun voicePhrase(st: State): String {
        val p = st.throttlePct?.toInt()?.let { "$it por ciento" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Acelerador relativo crítico. $p."
            "warn" -> "Cuidado. Acelerador relativo alto. $p."
            else -> "Acelerador relativo a $p."
        }
    }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFFE11D48
            "warn" -> 0xFFF97316
            "ok" -> 0xFF14B8A6
            else -> 0xFF94A3B8
        }

    fun toJsonMap(st: State): Map<String, Any?> =
        mapOf(
            "throttle_pct" to st.throttlePct?.toDouble(),
            "speed_kmh" to st.speedKmh.toDouble(),
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
