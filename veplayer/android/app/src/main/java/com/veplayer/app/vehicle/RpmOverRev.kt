package com.veplayer.app.vehicle

/**
 * Engine RPM over-rev bands (OBD PID 010C).
 */
object RpmOverRev {
    data class State(
        val rpm: Float? = null,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        rpm: Float?,
        warnRpm: Float = 4500f,
        alertRpm: Float = 5500f,
    ): State {
        if (rpm == null) {
            return State(band = "idle", label = "")
        }
        val r = rpm.coerceAtLeast(0f)
        val warn = warnRpm.coerceIn(2500f, 7000f)
        val alert = alertRpm.coerceAtLeast(warn + 100f)
        val band =
            when {
                r >= alert -> "alert"
                r >= warn -> "warn"
                else -> "ok"
            }
        return State(
            rpm = r,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "${r.toInt()} rpm",
        )
    }

    fun voicePhrase(st: State): String {
        val r = st.rpm?.toInt()?.let { "$it revoluciones" } ?: "elevadas"
        return when (st.band) {
            "alert" -> "Atención. Régimen del motor crítico. $r. Reduce aceleración."
            "warn" -> "Cuidado. Revoluciones altas. $r."
            else -> "Motor a $r."
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
