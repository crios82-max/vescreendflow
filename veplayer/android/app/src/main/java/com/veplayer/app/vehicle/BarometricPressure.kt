package com.veplayer.app.vehicle

/**
 * Barometric pressure bands (OBD PID 0133), kPa — out-of-range sensor alerts.
 */
object BarometricPressure {
    data class State(
        val baroKpa: Float? = null,
        val speedKmh: Float = 0f,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        baroKpa: Float?,
        speedKmh: Float = 0f,
        warnLowKpa: Float = 88f,
        alertLowKpa: Float = 82f,
        warnHighKpa: Float = 108f,
        alertHighKpa: Float = 112f,
        speedMinKmh: Float = 20f,
    ): State {
        if (baroKpa == null) {
            return State(band = "idle", label = "")
        }
        val baro = baroKpa.coerceIn(0f, 255f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warnLow = warnLowKpa.coerceIn(50f, 120f)
        val alertLow = alertLowKpa.coerceIn(40f, warnLow - 2f)
        val warnHigh = warnHighKpa.coerceIn(100f, 200f)
        val alertHigh = alertHighKpa.coerceAtLeast(warnHigh + 2f).coerceAtMost(255f)
        val minSpd = speedMinKmh.coerceIn(0f, 60f)

        if (speed < minSpd) {
            return State(
                baroKpa = baro,
                speedKmh = speed,
                band = "ok",
                label = if (baro in 85f..110f) "Baro · ${baro.toInt()} kPa" else "",
            )
        }

        val band =
            when {
                baro <= alertLow || baro >= alertHigh -> "alert"
                baro <= warnLow || baro >= warnHigh -> "warn"
                else -> "ok"
            }
        return State(
            baroKpa = baro,
            speedKmh = speed,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "Baro · ${baro.toInt()} kPa",
        )
    }

    fun voicePhrase(st: State): String {
        val k = st.baroKpa?.toInt()?.let { "$it kilopascales" } ?: "anómala"
        return when (st.band) {
            "alert" -> "Atención. Presión barométrica crítica. $k. Revisa sensor MAP/baro."
            "warn" -> "Cuidado. Presión barométrica fuera de rango. $k."
            else -> "Presión barométrica a $k."
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
            "baro_kpa" to st.baroKpa?.toDouble(),
            "speed_kmh" to st.speedKmh.toDouble(),
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
