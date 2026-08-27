package com.veplayer.app.vehicle

/**
 * Distance since DTC clear (OBD PID 0131), km — alert when faults persist.
 */
object DistSinceClear {
    data class State(
        val distanceKm: Float? = null,
        val faultActive: Boolean = false,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        distanceKm: Float?,
        faultActive: Boolean = false,
        warnKm: Float = 100f,
        alertKm: Float = 200f,
    ): State {
        if (distanceKm == null || !faultActive) {
            return State(
                distanceKm = distanceKm,
                faultActive = faultActive,
                band = if (distanceKm == null) "idle" else "ok",
                label =
                    if (faultActive && distanceKm != null) {
                        "Clear · ${distanceKm.toInt()} km"
                    } else {
                        ""
                    },
            )
        }
        val km = distanceKm.coerceAtLeast(0f)
        val warn = warnKm.coerceAtLeast(10f)
        val alert = alertKm.coerceAtLeast(warn + 10f)
        val band =
            when {
                km >= alert -> "alert"
                km >= warn -> "warn"
                else -> "ok"
            }
        return State(
            distanceKm = km,
            faultActive = faultActive,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "Clear · ${km.toInt()} km",
        )
    }

    fun voicePhrase(st: State): String {
        val km = st.distanceKm?.toInt()?.let { "$it kilómetros" } ?: "muchos kilómetros"
        return when (st.band) {
            "alert" ->
                "Atención. Llevas $km desde el último reset de fallas sin reparar. Revisa el vehículo."
            "warn" ->
                "Cuidado. $km desde limpiar códigos y la falla sigue. Programa servicio."
            else -> "Distancia desde clear. $km."
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
            "fault_active" to st.faultActive,
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
