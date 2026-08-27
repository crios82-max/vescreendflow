package com.veplayer.app.vehicle

/** Accelerator pedal position D (OBD PID 0149), %. */
object AccelPedal {
    data class State(
        val pedalPct: Float? = null,
        val speedKmh: Float = 0f,
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        pedalPct: Float?,
        speedKmh: Float = 0f,
        warnPct: Float = 80f,
        alertPct: Float = 92f,
        speedMinKmh: Float = 20f,
    ): State {
        if (pedalPct == null) return State(band = "idle", label = "")
        val pedal = pedalPct.coerceIn(0f, 100f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(50f, 95f)
        val alert = alertPct.coerceAtLeast(warn + 3f).coerceAtMost(100f)
        val minSpd = speedMinKmh.coerceIn(0f, 60f)
        if (speed < minSpd) {
            return State(
                pedalPct = pedal,
                speedKmh = speed,
                band = "ok",
                label = if (pedal >= 50f) "Pedal · ${pedal.toInt()}%" else "",
            )
        }
        val band =
            when {
                pedal >= alert -> "alert"
                pedal >= warn -> "warn"
                else -> "ok"
            }
        return State(
            pedalPct = pedal,
            speedKmh = speed,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "Pedal · ${pedal.toInt()}%",
        )
    }

    fun voicePhrase(st: State): String {
        val p = st.pedalPct?.toInt()?.let { "$it por ciento" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Pedal de acelerador crítico. $p. Modera."
            "warn" -> "Cuidado. Pedal de acelerador alto. $p."
            else -> "Pedal a $p."
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
            "pedal_pct" to st.pedalPct?.toDouble(),
            "speed_kmh" to st.speedKmh.toDouble(),
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
