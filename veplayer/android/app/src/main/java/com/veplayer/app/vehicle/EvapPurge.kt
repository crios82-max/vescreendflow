package com.veplayer.app.vehicle

/** Evap purge flow (OBD PID 014E), %. */
object EvapPurge {
    data class State(
        val purgePct: Float? = null,
        val speedKmh: Float = 0f,
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        purgePct: Float?,
        speedKmh: Float = 0f,
        warnPct: Float = 55f,
        alertPct: Float = 75f,
        speedMinKmh: Float = 20f,
    ): State {
        if (purgePct == null) return State(band = "idle", label = "")
        val pct = purgePct.coerceIn(0f, 100f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(20f, 90f)
        val alert = alertPct.coerceAtLeast(warn + 5f).coerceAtMost(100f)
        val minSpd = speedMinKmh.coerceIn(0f, 60f)
        if (speed < minSpd) {
            return State(
                purgePct = pct,
                speedKmh = speed,
                band = "ok",
                label = if (pct >= 30f) "Evap · ${pct.toInt()}%" else "",
            )
        }
        val band =
            when {
                pct >= alert -> "alert"
                pct >= warn -> "warn"
                else -> "ok"
            }
        return State(
            purgePct = pct,
            speedKmh = speed,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "Evap · ${pct.toInt()}%",
        )
    }

    fun voicePhrase(st: State): String {
        val p = st.purgePct?.toInt()?.let { "$it por ciento" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Purga evaporativo crítica. $p."
            "warn" -> "Cuidado. Purga evaporativo alta. $p."
            else -> "Purga evaporativo a $p."
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
            "purge_pct" to st.purgePct?.toDouble(),
            "speed_kmh" to st.speedKmh.toDouble(),
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
