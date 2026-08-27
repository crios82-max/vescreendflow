package com.veplayer.app.vehicle

/**
 * Engine fuel rate bands (OBD PID 015E) — grams/sec → L/h display.
 */
object FuelRate {
    /** Gasoline density ~0.74 kg/L for L/h conversion. */
    private const val GRAMS_PER_LITER = 740f

    data class State(
        val fuelRateGps: Float? = null,
        val fuelRateLph: Float? = null,
        val speedKmh: Float = 0f,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun gpsToLph(gps: Float): Float = gps * 3600f / GRAMS_PER_LITER

    fun lphToGps(lph: Float): Float = lph * GRAMS_PER_LITER / 3600f

    fun evaluate(
        fuelRateGps: Float?,
        speedKmh: Float = 0f,
        warnLph: Float = 55f,
        alertLph: Float = 80f,
        speedMinKmh: Float = 20f,
    ): State {
        if (fuelRateGps == null) {
            return State(band = "idle", label = "")
        }
        val gps = fuelRateGps.coerceAtLeast(0f)
        val lph = gpsToLph(gps)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnLph.coerceIn(10f, 200f)
        val alert = alertLph.coerceAtLeast(warn + 5f).coerceAtMost(250f)
        val minSpd = speedMinKmh.coerceIn(0f, 60f)

        if (speed < minSpd) {
            return State(
                fuelRateGps = gps,
                fuelRateLph = lph,
                speedKmh = speed,
                band = "ok",
                label = if (lph >= 20f) "Comb · ${lph.toInt()} L/h" else "",
            )
        }

        val band =
            when {
                lph >= alert -> "alert"
                lph >= warn -> "warn"
                else -> "ok"
            }
        return State(
            fuelRateGps = gps,
            fuelRateLph = lph,
            speedKmh = speed,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "Comb · ${lph.toInt()} L/h",
        )
    }

    fun voicePhrase(st: State): String {
        val l = st.fuelRateLph?.toInt()?.let { "$it litros por hora" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Consumo de combustible crítico. $l. Reduce velocidad."
            "warn" -> "Cuidado. Consumo alto. $l."
            else -> "Consumo a $l."
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
            "fuel_rate_gps" to st.fuelRateGps?.toDouble(),
            "fuel_rate_lph" to st.fuelRateLph?.toDouble(),
            "speed_kmh" to st.speedKmh.toDouble(),
            "band" to st.band,
            "show_warn" to st.showWarn,
        )
}
