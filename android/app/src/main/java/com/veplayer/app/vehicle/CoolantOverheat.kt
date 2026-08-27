package com.veplayer.app.vehicle

/**
 * Engine coolant overheat bands.
 */
object CoolantOverheat {
    data class State(
        val coolantC: Float? = null,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        coolantC: Float?,
        warnC: Float = 105f,
        alertC: Float = 115f,
    ): State {
        if (coolantC == null) {
            return State(band = "idle", label = "")
        }
        val c = coolantC
        val warn = warnC.coerceAtLeast(80f)
        val alert = alertC.coerceAtLeast(warn + 1f)
        val band =
            when {
                c >= alert -> "alert"
                c >= warn -> "warn"
                else -> "ok"
            }
        return State(
            coolantC = c,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "${c.toInt()}°C",
        )
    }

    fun voicePhrase(st: State): String {
        val c = st.coolantC?.toInt()?.let { "$it grados" } ?: "elevada"
        return when (st.band) {
            "alert" -> "Atención. Temperatura del motor crítica. $c. Detén el vehículo con seguridad."
            "warn" -> "Cuidado. Motor caliente. Refrigerante a $c."
            else -> "Refrigerante a $c."
        }
    }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFFE11D48
            "warn" -> 0xFFF97316
            "ok" -> 0xFF14B8A6
            else -> 0xFF94A3B8
        }
}
