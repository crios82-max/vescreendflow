package com.veplayer.app.vehicle

/**
 * Cabin over-temperature bands (heat soak / AC failure).
 */
object CabinOvertemp {
    data class State(
        val cabinC: Float? = null,
        val outdoorC: Float? = null,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        cabinC: Float?,
        outdoorC: Float? = null,
        warnC: Float = 32f,
        alertC: Float = 38f,
    ): State {
        if (cabinC == null) {
            return State(outdoorC = outdoorC, band = "idle", label = "")
        }
        val cabin = cabinC
        val warn = warnC.coerceAtLeast(20f)
        val alert = alertC.coerceAtLeast(warn + 1f)
        val band =
            when {
                cabin >= alert -> "alert"
                cabin >= warn -> "warn"
                else -> "ok"
            }
        return State(
            cabinC = cabin,
            outdoorC = outdoorC,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "${cabin.toInt()}°C",
        )
    }

    fun voicePhrase(st: State): String {
        val c = st.cabinC?.toInt()?.let { "$it grados" } ?: "elevada"
        return when (st.band) {
            "alert" -> "Atención. Temperatura de cabina crítica. $c. Ventila o enciende el aire."
            "warn" -> "Cuidado. Cabina caliente. $c."
            else -> "Cabina a $c."
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
