package com.veplayer.app.vehicle

/** Ethanol fuel % (OBD PID 0152). */
object EthanolPct {
    data class State(
        val ethanolPct: Float? = null,
        val speedKmh: Float = 0f,
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        ethanolPct: Float?,
        speedKmh: Float = 0f,
        warnPct: Float = 70f,
        alertPct: Float = 85f,
        speedMinKmh: Float = 20f,
    ): State {
        if (ethanolPct == null) return State(band = "idle", label = "")
        val pct = ethanolPct.coerceIn(0f, 100f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(30f, 95f)
        val alert = alertPct.coerceAtLeast(warn + 5f).coerceAtMost(100f)
        val minSpd = speedMinKmh.coerceIn(0f, 60f)
        if (speed < minSpd) {
            return State(
                ethanolPct = pct,
                speedKmh = speed,
                band = "ok",
                label = if (pct >= 15f) "Etanol · ${pct.toInt()}%" else "",
            )
        }
        val band =
            when {
                pct >= alert -> "alert"
                pct >= warn -> "warn"
                else -> "ok"
            }
        return State(
            ethanolPct = pct,
            speedKmh = speed,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "Etanol · ${pct.toInt()}%",
        )
    }

    fun voicePhrase(st: State): String {
        val p = st.ethanolPct?.toInt()?.let { "$it por ciento" } ?: "alto"
        return when (st.band) {
            "alert" -> "Atención. Contenido de etanol crítico. $p. Verifica combustible."
            "warn" -> "Cuidado. Etanol alto en mezcla. $p."
            else -> "Etanol a $p."
        }
    }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFFE11D48
            "warn" -> 0xFFF59E0B
            "ok" -> 0xFF14B8A6
            else -> 0xFF94A3B8
        }

    fun toJsonMap(st: State): Map<String, Any?> =
        mapOf(
            "ethanol_pct" to st.ethanolPct?.toDouble(),
            "speed_kmh" to st.speedKmh.toDouble(),
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
