package com.veplayer.app.vehicle

/**
 * Distance driven with MIL on (OBD PID 0121), km.
 */
object MilDistance {
    data class State(
        val distanceKm: Float? = null,
        val milOn: Boolean = false,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        distanceKm: Float?,
        milOn: Boolean = false,
        warnKm: Float = 50f,
        alertKm: Float = 100f,
    ): State {
        if (distanceKm == null || !milOn) {
            return State(
                distanceKm = distanceKm,
                milOn = milOn,
                band = if (distanceKm == null) "idle" else "ok",
                label = if (milOn && distanceKm != null) "MIL · ${distanceKm.toInt()} km" else "",
            )
        }
        val km = distanceKm.coerceAtLeast(0f)
        val warn = warnKm.coerceAtLeast(5f)
        val alert = alertKm.coerceAtLeast(warn + 5f)
        val band =
            when {
                km >= alert -> "alert"
                km >= warn -> "warn"
                else -> "ok"
            }
        return State(
            distanceKm = km,
            milOn = milOn,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "MIL · ${km.toInt()} km",
        )
    }

    fun voicePhrase(st: State): String {
        val km = st.distanceKm?.toInt()?.let { "$it kilómetros" } ?: "muchos kilómetros"
        return when (st.band) {
            "alert" ->
                "Atención. Llevas $km con la luz de motor encendida. Revisa el vehículo pronto."
            "warn" ->
                "Cuidado. Has recorrido $km con MIL activa. Programa revisión."
            else -> "Distancia con MIL. $km."
        }
    }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFFE11D48
            "warn" -> 0xFFF59E0B
            "ok" -> 0xFF14B8A6
            else -> 0xFF94A3B8
        }

    fun toJsonMap(st: State): Map<String, Any?> =
        mapOf(
            "distance_km" to st.distanceKm?.toDouble(),
            "mil_on" to st.milOn,
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
