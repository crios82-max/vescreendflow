package com.veplayer.app.vehicle

/**
 * Catalyst temperature bands (OBD PID 0134), °C.
 */
object CatalystTemp {
    data class State(
        val catalystTempC: Float? = null,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        catalystTempC: Float?,
        warnC: Float = 750f,
        alertC: Float = 850f,
    ): State {
        if (catalystTempC == null) {
            return State(band = "idle", label = "")
        }
        val c = catalystTempC.coerceIn(-40f, 1200f)
        val warn = warnC.coerceAtLeast(400f)
        val alert = alertC.coerceAtLeast(warn + 10f)
        val band =
            when {
                c >= alert -> "alert"
                c >= warn -> "warn"
                else -> "ok"
            }
        val label = "Cat · ${c.toInt()}°C"
        return State(
            catalystTempC = c,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = label,
        )
    }

    fun voicePhrase(st: State): String {
        val c = st.catalystTempC?.toInt()?.let { "$it grados" } ?: "elevada"
        return when (st.band) {
            "alert" -> "Atención. Catalizador crítico. $c. Reduce carga y revisa motor."
            "warn" -> "Cuidado. Catalizador muy caliente. $c."
            else -> "Catalizador a $c."
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
            "catalyst_temp_c" to st.catalystTempC?.toDouble(),
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
