package com.veplayer.app.vehicle

/**
 * Mass air flow bands (OBD PID 0110), grams/sec.
 */
object MafAirflow {
    data class State(
        val mafGps: Float? = null,
        val speedKmh: Float = 0f,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        mafGps: Float?,
        speedKmh: Float = 0f,
        warnGps: Float = 80f,
        alertGps: Float = 110f,
        speedMinKmh: Float = 20f,
    ): State {
        if (mafGps == null) {
            return State(band = "idle", label = "")
        }
        val gps = mafGps.coerceAtLeast(0f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnGps.coerceIn(20f, 300f)
        val alert = alertGps.coerceAtLeast(warn + 10f).coerceAtMost(400f)
        val minSpd = speedMinKmh.coerceIn(0f, 60f)

        if (speed < minSpd) {
            return State(
                mafGps = gps,
                speedKmh = speed,
                band = "ok",
                label = if (gps >= 25f) "MAF · ${gps.toInt()} g/s" else "",
            )
        }

        val band =
            when {
                gps >= alert -> "alert"
                gps >= warn -> "warn"
                else -> "ok"
            }
        return State(
            mafGps = gps,
            speedKmh = speed,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "MAF · ${gps.toInt()} g/s",
        )
    }

    fun voicePhrase(st: State): String {
        val g = st.mafGps?.toInt()?.let { "$it gramos por segundo" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Flujo de aire MAF crítico. $g. Reduce demanda."
            "warn" -> "Cuidado. Flujo de aire alto. $g."
            else -> "Flujo MAF a $g."
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
            "maf_gps" to st.mafGps?.toDouble(),
            "speed_kmh" to st.speedKmh.toDouble(),
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
