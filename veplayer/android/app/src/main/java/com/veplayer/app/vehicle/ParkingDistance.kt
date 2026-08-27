package com.veplayer.app.vehicle

/**
 * Parking ultrasonic / PDC distance math (rear L/C/R).
 * Bands align with reverse guide markers (~1 / 2 / 4 m).
 */
object ParkingDistance {
    data class Zones(
        val rearL: Float? = null,
        val rearC: Float? = null,
        val rearR: Float? = null,
    ) {
        fun toJsonMap(): Map<String, Any?> =
            mapOf(
                "rear_l_m" to rearL?.toDouble(),
                "rear_c_m" to rearC?.toDouble(),
                "rear_r_m" to rearR?.toDouble(),
            )
    }

    data class State(
        val active: Boolean = false,
        val closestM: Float? = null,
        /** ok | near | warn | crit */
        val band: String = "ok",
        val zones: Zones = Zones(),
        val label: String = "",
        val showWarn: Boolean = false,
    )

    fun evaluate(
        zones: Zones,
        reverse: Boolean,
        warnM: Float = 1.5f,
        critM: Float = 0.6f,
        nearM: Float = 2.5f,
    ): State {
        if (!reverse) {
            return State(active = false, label = "")
        }
        val vals = listOfNotNull(zones.rearL, zones.rearC, zones.rearR).filter { it > 0f }
        if (vals.isEmpty()) {
            return State(active = true, zones = zones, label = "PDC…", band = "ok")
        }
        val closest = vals.minOrNull()!!
        val band =
            when {
                closest <= critM -> "crit"
                closest <= warnM -> "warn"
                closest <= nearM -> "near"
                else -> "ok"
            }
        val side =
            when (closest) {
                zones.rearC -> "centro"
                zones.rearL -> "izq"
                zones.rearR -> "der"
                else -> ""
            }
        return State(
            active = true,
            closestM = closest,
            band = band,
            zones = zones,
            label = "${"%.1f".format(closest)} m · $side".trimEnd(' ', '·'),
            showWarn = band == "warn" || band == "crit",
        )
    }

    fun voicePhrase(st: State): String {
        val m = st.closestM ?: return "Atención. Obstáculo detrás."
        val meters =
            if (m < 1f) {
                "${(m * 10).toInt() / 10.0} metros"
            } else {
                "${m.toInt()} metros"
            }
        return when (st.band) {
            "crit" -> "Alto. Obstáculo muy cerca, $meters."
            "warn" -> "Cuidado. Obstáculo atrás, $meters."
            else -> "Obstáculo atrás, $meters."
        }
    }

    fun accentArgb(band: String): Long =
        when (band) {
            "crit" -> 0xFFE11D48
            "warn" -> 0xFFF59E0B
            "near" -> 0xFFEAB308
            else -> 0xFF22C55E
        }

    /** Fill 0..1 for HUD bar (closer = fuller). */
    fun barFill(
        meters: Float?,
        maxM: Float = 4f,
    ): Float {
        if (meters == null || meters <= 0f) return 0f
        return (1f - (meters / maxM).coerceIn(0f, 1f)).coerceIn(0f, 1f)
    }
}
