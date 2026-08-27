package com.veplayer.app.vehicle

/** Absolute engine load (OBD PID 0143), %. */
object AbsoluteLoad {
    data class State(
        val loadPct: Float? = null,
        val speedKmh: Float = 0f,
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        loadPct: Float?,
        speedKmh: Float = 0f,
        warnPct: Float = 85f,
        alertPct: Float = 95f,
        speedMinKmh: Float = 20f,
    ): State {
        if (loadPct == null) return State(band = "idle", label = "")
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
                label = if (load >= 55f) "AbsL · ${load.toInt()}%" else "",
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
            label = "AbsL · ${load.toInt()}%",
        )
    }

    fun voicePhrase(st: State): String {
        val p = st.loadPct?.toInt()?.let { "$it por ciento" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Carga absoluta crítica. $p. Reduce demanda."
            "warn" -> "Cuidado. Carga absoluta alta. $p."
            else -> "Carga absoluta a $p."
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
            "label" to st.label,
        )
}
