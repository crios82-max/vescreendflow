package com.veplayer.app.vehicle

/**
 * Vehicle rolling in Park or Neutral (gear not engaged).
 */
object GearRoll {
    data class State(
        val gear: String = "",
        val speedKmh: Float = 0f,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun isRollGear(gear: Gear): Boolean = gear == Gear.P || gear == Gear.N

    fun evaluate(
        gear: Gear,
        speedKmh: Float,
        warnKmh: Float = 5f,
        alertKmh: Float = 20f,
    ): State {
        val speed = speedKmh.coerceAtLeast(0f)
        val g = gear.name
        if (!isRollGear(gear)) {
            return State(gear = g, speedKmh = speed, band = "ok", label = "")
        }
        val warn = warnKmh.coerceIn(1f, 40f)
        val alert = alertKmh.coerceAtLeast(warn + 1f)
        if (speed < warn) {
            return State(
                gear = g,
                speedKmh = speed,
                band = "idle",
                label = "Marcha $g",
            )
        }
        val band = if (speed >= alert) "alert" else "warn"
        return State(
            gear = g,
            speedKmh = speed,
            band = band,
            showWarn = true,
            label = "$g · ${speed.toInt()} km/h",
        )
    }

    fun voicePhrase(st: State): String {
        val g =
            when (st.gear) {
                "P" -> "parking"
                "N" -> "neutral"
                else -> st.gear.lowercase()
            }
        return when (st.band) {
            "alert" ->
                "Atención. Vehículo en movimiento en $g. Pon marcha o frena."
            "warn" ->
                "Cuidado. Te estás desplazando en $g."
            else -> "Marcha $g."
        }
    }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFFE11D48
            "warn" -> 0xFFF59E0B
            else -> 0xFF94A3B8
        }
}
