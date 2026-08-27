package com.veplayer.app.vehicle

/** EGR error (OBD PID 014D), signed %. */
object EgrError {
    data class State(
        val errorPct: Float? = null,
        val speedKmh: Float = 0f,
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun formatPct(pct: Float): String {
        val r = pct.toInt()
        return if (r >= 0) "+$r%" else "$r%"
    }

    fun evaluate(
        errorPct: Float?,
        speedKmh: Float = 0f,
        warnPct: Float = 15f,
        alertPct: Float = 25f,
        speedMinKmh: Float = 20f,
    ): State {
        if (errorPct == null) return State(band = "idle", label = "")
        val err = errorPct.coerceIn(-50f, 50f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(5f, 40f)
        val alert = alertPct.coerceAtLeast(warn + 3f).coerceAtMost(50f)
        val minSpd = speedMinKmh.coerceIn(0f, 60f)
        val absErr = kotlin.math.abs(err)
        if (speed < minSpd) {
            return State(
                errorPct = err,
                speedKmh = speed,
                band = "ok",
                label = if (absErr >= 10f) "EGR · ${formatPct(err)}" else "",
            )
        }
        val band =
            when {
                absErr >= alert -> "alert"
                absErr >= warn -> "warn"
                else -> "ok"
            }
        return State(
            errorPct = err,
            speedKmh = speed,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "EGR · ${formatPct(err)}",
        )
    }

    fun voicePhrase(st: State): String {
        val p = st.errorPct?.let { formatPct(it) } ?: "alto"
        return when (st.band) {
            "alert" -> "Atención. Error EGR crítico. $p. Revisa válvula EGR."
            "warn" -> "Cuidado. Error EGR fuera de rango. $p."
            else -> "Error EGR a $p."
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
            "error_pct" to st.errorPct?.toDouble(),
            "speed_kmh" to st.speedKmh.toDouble(),
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
