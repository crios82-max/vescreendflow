package com.veplayer.app.vehicle

/**
 * Off-route distance vs active nav polyline (warn / alert bands).
 */
object RouteDeviation {
    data class State(
        val distanceM: Float = 0f,
        val offRouteSec: Float = 0f,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
        val hasRoute: Boolean = false,
    )

    fun evaluate(
        distanceM: Float,
        offRouteSec: Float = 0f,
        hasRoute: Boolean = true,
        warnM: Float = 80f,
        alertM: Float = 150f,
        holdSec: Float = 8f,
    ): State {
        if (!hasRoute) {
            return State(band = "idle", hasRoute = false)
        }
        val dist = distanceM.coerceAtLeast(0f)
        val warn = warnM.coerceIn(20f, 500f)
        val alert = alertM.coerceAtLeast(warn + 10f)
        val hold = holdSec.coerceIn(0f, 120f)
        val band =
            when {
                dist >= alert -> "alert"
                dist >= warn -> "warn"
                else -> "ok"
            }
        val held = offRouteSec >= hold
        val showWarn = (band == "warn" || band == "alert") && held
        val label =
            when (band) {
                "alert" -> "Fuera ruta · ${dist.toInt()} m"
                "warn" -> "Desvío · ${dist.toInt()} m"
                "ok" -> if (dist > 5f) "En ruta · ${dist.toInt()} m" else "En ruta"
                else -> ""
            }
        return State(
            distanceM = dist,
            offRouteSec = offRouteSec.coerceAtLeast(0f),
            band = band,
            showWarn = showWarn,
            label = label,
            hasRoute = true,
        )
    }

    fun voicePhrase(st: State): String =
        when (st.band) {
            "alert" ->
                "Atención. Estás muy lejos de la ruta. ${st.distanceM.toInt()} metros de desvío."
            "warn" ->
                "Cuidado. Te estás desviando de la ruta. ${st.distanceM.toInt()} metros."
            else -> "Ruta activa."
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
            "band" to st.band,
            "distance_m" to st.distanceM.toDouble(),
            "off_route_sec" to st.offRouteSec.toDouble(),
            "show_warn" to st.showWarn,
            "has_route" to st.hasRoute,
        )
}
