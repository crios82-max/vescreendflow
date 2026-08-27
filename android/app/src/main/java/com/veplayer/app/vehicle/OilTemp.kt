package com.veplayer.app.vehicle

/**
 * Engine oil temperature bands (OBD PID 015C).
 */
object OilTemp {
    data class State(
        val oilTempC: Float? = null,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        oilTempC: Float?,
        warnC: Float = 120f,
        alertC: Float = 130f,
    ): State {
        if (oilTempC == null) {
            return State(band = "idle", label = "")
        }
        val c = oilTempC
        val warn = warnC.coerceAtLeast(90f)
        val alert = alertC.coerceAtLeast(warn + 1f)
        val band =
            when {
                c >= alert -> "alert"
                c >= warn -> "warn"
                else -> "ok"
            }
        return State(
            oilTempC = c,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "${c.toInt()}°C",
        )
    }

    fun voicePhrase(st: State): String {
        val c = st.oilTempC?.toInt()?.let { "$it grados" } ?: "elevada"
        return when (st.band) {
            "alert" -> "Atención. Aceite del motor crítico. $c. Detén el vehículo con seguridad."
            "warn" -> "Cuidado. Aceite caliente. $c."
            else -> "Aceite a $c."
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
            "oil_temp_c" to st.oilTempC?.toDouble(),
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
