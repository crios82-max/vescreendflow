package com.veplayer.app.vehicle

/**
 * Short-term fuel trim bands (OBD PID 0108), signed %.
 */
object FuelTrimStft {
    data class State(
        val trimPct: Float? = null,
        val speedKmh: Float = 0f,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun formatTrimPct(pct: Float): String {
        val rounded = pct.toInt()
        return if (rounded >= 0) "+$rounded%" else "$rounded%"
    }

    fun evaluate(
        trimPct: Float?,
        speedKmh: Float = 0f,
        warnPct: Float = 12f,
        alertPct: Float = 20f,
        speedMinKmh: Float = 20f,
    ): State {
        if (trimPct == null) {
            return State(band = "idle", label = "")
        }
        val trim = trimPct.coerceIn(-50f, 50f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(5f, 40f)
        val alert = alertPct.coerceAtLeast(warn + 3f).coerceAtMost(50f)
        val minSpd = speedMinKmh.coerceIn(0f, 60f)
        val absTrim = kotlin.math.abs(trim)

        if (speed < minSpd) {
            return State(
                trimPct = trim,
                speedKmh = speed,
                band = "ok",
                label = if (absTrim >= 8f) "STB2 · ${formatTrimPct(trim)}" else "",
            )
        }

        val band =
            when {
                absTrim >= alert -> "alert"
                absTrim >= warn -> "warn"
                else -> "ok"
            }
        return State(
            trimPct = trim,
            speedKmh = speed,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "STB2 · ${formatTrimPct(trim)}",
        )
    }

    fun voicePhrase(st: State): String {
        val p = st.trimPct?.let { formatTrimPct(it).replace("+", "más ") } ?: "alto"
        return when (st.band) {
            "alert" -> "Atención. Corrección de combustible STFT B2 crítica. $p. Revisa motor."
            "warn" -> "Cuidado. Corrección STFT B2 B2 fuera de rango. $p."
            else -> "Corrección STFT B2 a $p."
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
            "trim_pct" to st.trimPct?.toDouble(),
            "speed_kmh" to st.speedKmh.toDouble(),
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
