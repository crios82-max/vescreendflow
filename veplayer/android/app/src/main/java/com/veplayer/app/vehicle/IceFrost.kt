package com.veplayer.app.vehicle

/**
 * Outdoor ice / frost risk (OBD ambient 0146 / outdoorTempC).
 * Cold bands: warn ≤ warnC · alert ≤ alertC.
 */
object IceFrost {
    data class State(
        val outdoorC: Float? = null,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        outdoorC: Float?,
        warnC: Float = 3f,
        alertC: Float = 0f,
    ): State {
        if (outdoorC == null) {
            return State(band = "idle", label = "")
        }
        val t = outdoorC
        val alert = alertC.coerceIn(-20f, 5f)
        val warn = warnC.coerceAtLeast(alert + 0.5f).coerceAtMost(10f)
        val band =
            when {
                t <= alert -> "alert"
                t <= warn -> "warn"
                else -> "ok"
            }
        return State(
            outdoorC = t,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "${"%.0f".format(t)}°C",
        )
    }

    fun voicePhrase(st: State): String {
        val c = st.outdoorC?.let { "${"%.0f".format(it)} grados" } ?: "baja"
        return when (st.band) {
            "alert" -> "Atención. Riesgo de hielo. Temperatura exterior $c. Reduce velocidad."
            "warn" -> "Cuidado. Posible escarcha. Exterior a $c."
            else -> "Exterior a $c."
        }
    }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFF38BDF8
            "warn" -> 0xFF67E8F9
            "ok" -> 0xFF14B8A6
            else -> 0xFF94A3B8
        }
}
