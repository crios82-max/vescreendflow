package com.veplayer.app.vehicle

/**
 * Hazard lights left on too long while moving.
 */
object HazardStuck {
    data class State(
        val heldSec: Float = 0f,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
        val active: Boolean = false,
    )

    fun evaluate(
        active: Boolean,
        heldSec: Float,
        warnSec: Float = 45f,
        alertSec: Float = 90f,
    ): State {
        if (!active) {
            return State(band = "idle", label = "")
        }
        val held = heldSec.coerceAtLeast(0f)
        val warn = warnSec.coerceIn(15f, 300f)
        val alert = alertSec.coerceAtLeast(warn + 10f)
        val band =
            when {
                held >= alert -> "alert"
                held >= warn -> "warn"
                else -> "ok"
            }
        return State(
            heldSec = held,
            band = band,
            showWarn = band == "warn" || band == "alert",
            active = true,
            label =
                when (band) {
                    "alert", "warn" -> "Hazard · ${held.toInt()}s"
                    else -> "Hazard"
                },
        )
    }

    fun voicePhrase(st: State): String =
        when (st.band) {
            "alert" ->
                "Atención. Luces de emergencia olvidadas. Llevas ${st.heldSec.toInt()} segundos."
            "warn" ->
                "Cuidado. Las luces de emergencia siguen encendidas."
            else -> "Luces de emergencia activas."
        }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFFE11D48
            "warn" -> 0xFFF59E0B
            "ok" -> 0xFFEAB308
            else -> 0xFF94A3B8
        }
}
