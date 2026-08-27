package com.veplayer.app.vehicle

/**
 * Calculated engine load bands (OBD PID 0104).
 */
object EngineLoad {
    data class State(
        val loadPct: Float? = null,
        val speedKmh: Float = 0f,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        loadPct: Float?,
        speedKmh: Float = 0f,
        warnPct: Float = 80f,
        alertPct: Float = 92f,
        speedMinKmh: Float = 20f,
    ): State {
        if (loadPct == null) {
            return State(band = "idle", label = "")
        }
        val load = loadPct.coerceIn(0f, 100f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(50f, 98f)
        val alert = alertPct.coerceAtLeast(warn + 3f).coerceAtMost(100f)
        val minSpd = speedMinKmh.coerceIn(0f, 60f)

        if (speed < minSpd) {
            return State(
                loadPct = load,
                speedKmh = speed,
                band = "ok",
                label = if (load >= 50f) "Carga · ${load.toInt()}%" else "",
            )
        }

        val band =
            when {
                load >= alert -> "alert"
                load >= warn -> "warn"
                else -> "ok"
            }
        return State(
            loadPct = load,
            speedKmh = speed,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "Carga · ${load.toInt()}%",
        )
    }

    fun voicePhrase(st: State): String {
        val p = st.loadPct?.toInt()?.let { "$it por ciento" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Carga del motor crítica. $p. Reduce demanda."
            "warn" -> "Cuidado. Carga del motor alta. $p."
            else -> "Carga del motor a $p."
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
            "load_pct" to st.loadPct?.toDouble(),
            "speed_kmh" to st.speedKmh.toDouble(),
            "band" to st.band,
            "show_warn" to st.showWarn,
        )
}
