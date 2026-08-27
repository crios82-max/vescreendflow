package com.veplayer.app.vehicle

/**
 * Door / trunk / hood ajar while moving.
 */
object DoorAjar {
    data class State(
        val openLabels: List<String> = emptyList(),
        val speedKmh: Float = 0f,
        /** closed | ajar | warn | alert */
        val band: String = "closed",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun openLabels(s: VehicleSignals): List<String> {
        val out = mutableListOf<String>()
        if (s.doorFl) out += "FL"
        if (s.doorFr) out += "FR"
        if (s.doorRl) out += "RL"
        if (s.doorRr) out += "RR"
        if (s.trunkOpen) out += "baúl"
        if (s.hoodOpen) out += "capó"
        return out
    }

    fun evaluate(
        signals: VehicleSignals,
        warnKmh: Float = 5f,
        alertKmh: Float = 20f,
    ): State {
        val labels = openLabels(signals)
        val speed = signals.speedKmh.coerceAtLeast(0f)
        if (labels.isEmpty()) {
            return State(speedKmh = speed, band = "closed", label = "")
        }
        val band =
            when {
                signals.reverse || speed >= alertKmh -> "alert"
                speed >= warnKmh -> "warn"
                else -> "ajar"
            }
        return State(
            openLabels = labels,
            speedKmh = speed,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = labels.joinToString("+"),
        )
    }

    fun voicePhrase(st: State): String {
        val doors = st.openLabels.joinToString(", ")
        return when (st.band) {
            "alert" -> "Atención. Puerta abierta en movimiento. $doors."
            "warn" -> "Cuidado. Puerta abierta. $doors."
            else -> "Puerta abierta. $doors."
        }
    }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFFE11D48
            "warn" -> 0xFFF59E0B
            "ajar" -> 0xFFEAB308
            else -> 0xFF94A3B8
        }
}
