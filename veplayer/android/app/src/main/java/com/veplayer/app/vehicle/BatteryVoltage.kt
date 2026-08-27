package com.veplayer.app.vehicle

/**
 * 12V battery / control-module voltage bands (OBD PID 0142).
 * Low voltage → warn / alert (inverse of coolant thresholds).
 */
object BatteryVoltage {
    data class State(
        val volts: Float? = null,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        volts: Float?,
        warnV: Float = 12.0f,
        alertV: Float = 11.5f,
    ): State {
        if (volts == null) {
            return State(band = "idle", label = "")
        }
        val v = volts.coerceAtLeast(0f)
        val warn = warnV.coerceIn(10f, 13.5f)
        val alert = alertV.coerceIn(9f, warn - 0.05f)
        val band =
            when {
                v < alert -> "alert"
                v < warn -> "warn"
                else -> "ok"
            }
        return State(
            volts = v,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = String.format("%.1f V", v),
        )
    }

    fun voicePhrase(st: State): String {
        val v = st.volts?.let { String.format("%.1f voltios", it) } ?: "baja"
        return when (st.band) {
            "alert" -> "Atención. Batería crítica. $v. Revisa el sistema eléctrico."
            "warn" -> "Cuidado. Voltaje de batería bajo. $v."
            else -> "Batería a $v."
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
