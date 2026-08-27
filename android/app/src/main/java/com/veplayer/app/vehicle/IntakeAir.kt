package com.veplayer.app.vehicle

/**
 * Intake air temperature bands (OBD PID 010F) — high IAT / heat soak.
 */
object IntakeAir {
    data class State(
        val intakeAirC: Float? = null,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        intakeAirC: Float?,
        warnC: Float = 50f,
        alertC: Float = 60f,
    ): State {
        if (intakeAirC == null) {
            return State(band = "idle", label = "")
        }
        val t = intakeAirC
        val warn = warnC.coerceAtLeast(30f)
        val alert = alertC.coerceAtLeast(warn + 1f)
        val band =
            when {
                t >= alert -> "alert"
                t >= warn -> "warn"
                else -> "ok"
            }
        return State(
            intakeAirC = t,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "${t.toInt()}°C",
        )
    }

    fun voicePhrase(st: State): String {
        val c = st.intakeAirC?.toInt()?.let { "$it grados" } ?: "elevada"
        return when (st.band) {
            "alert" ->
                "Atención. Aire de admisión muy caliente. $c. Reduce carga o detente."
            "warn" ->
                "Cuidado. Admisión caliente. Aire a $c."
            else -> "Admisión a $c."
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
            "intake_air_c" to st.intakeAirC?.toDouble(),
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
