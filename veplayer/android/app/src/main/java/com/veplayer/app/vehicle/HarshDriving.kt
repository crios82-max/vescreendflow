package com.veplayer.app.vehicle

/**
 * Harsh braking / acceleration from longitudinal speed change (km/h per second).
 * Positive accelKmhS = accelerate · negative = brake.
 */
object HarshDriving {
    data class State(
        val accelKmhS: Float = 0f,
        /** ok | accel_warn | accel_alert | brake_warn | brake_alert */
        val band: String = "ok",
        val kind: String = "", // brake | accel | ""
        val showWarn: Boolean = false,
        val label: String = "",
        val absActive: Boolean = false,
    )

    fun evaluate(
        accelKmhS: Float,
        absActive: Boolean = false,
        brakeWarnKmhS: Float = 12f,
        brakeAlertKmhS: Float = 18f,
        accelWarnKmhS: Float = 10f,
        accelAlertKmhS: Float = 15f,
    ): State {
        val a = accelKmhS
        val brakeMag = (-a).coerceAtLeast(0f)
        val accelMag = a.coerceAtLeast(0f)
        val brakeAlert = brakeAlertKmhS.coerceAtLeast(brakeWarnKmhS + 1f)
        val accelAlert = accelAlertKmhS.coerceAtLeast(accelWarnKmhS + 1f)

        // ABS during deceleration escalates to alert
        if (brakeMag >= brakeWarnKmhS || (absActive && brakeMag >= brakeWarnKmhS * 0.6f)) {
            val band =
                when {
                    brakeMag >= brakeAlert || absActive && brakeMag >= brakeWarnKmhS -> "brake_alert"
                    else -> "brake_warn"
                }
            return State(
                accelKmhS = a,
                band = band,
                kind = "brake",
                showWarn = true,
                label = "Frenada · ${"%.0f".format(brakeMag)}",
                absActive = absActive,
            )
        }
        if (accelMag >= accelWarnKmhS) {
            val band = if (accelMag >= accelAlert) "accel_alert" else "accel_warn"
            return State(
                accelKmhS = a,
                band = band,
                kind = "accel",
                showWarn = true,
                label = "Acel. · ${"%.0f".format(accelMag)}",
                absActive = absActive,
            )
        }
        return State(accelKmhS = a, band = "ok", absActive = absActive)
    }

    fun voicePhrase(st: State): String =
        when (st.band) {
            "brake_alert" ->
                if (st.absActive) {
                    "Atención. Frenada brusca con ABS."
                } else {
                    "Atención. Frenada brusca."
                }
            "brake_warn" -> "Cuidado. Frenada fuerte."
            "accel_alert" -> "Atención. Aceleración brusca."
            "accel_warn" -> "Cuidado. Aceleración fuerte."
            else -> ""
        }

    fun accentArgb(band: String): Long =
        when (band) {
            "brake_alert", "accel_alert" -> 0xFFE11D48
            "brake_warn", "accel_warn" -> 0xFFF59E0B
            else -> 0xFF94A3B8
        }

    fun toJsonMap(st: State): Map<String, Any?> =
        mapOf(
            "kind" to st.kind.ifBlank { null },
            "band" to st.band,
            "accel_kmh_s" to st.accelKmhS.toDouble(),
            "abs" to st.absActive,
        )
}
