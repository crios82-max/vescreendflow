package com.veplayer.app.vehicle

/**
 * Turn signal LEFT/RIGHT held too long while moving (forgotten blinker).
 * Hazards do not count.
 */
object TurnStuck {
    data class State(
        val side: String = "", // left | right | ""
        val heldSec: Float = 0f,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        side: String,
        heldSec: Float,
        warnSec: Float = 30f,
        alertSec: Float = 60f,
    ): State {
        val s = side.lowercase().trim()
        if (s != "left" && s != "right") {
            return State(band = "idle", label = "")
        }
        val held = heldSec.coerceAtLeast(0f)
        val warn = warnSec.coerceIn(10f, 180f)
        val alert = alertSec.coerceAtLeast(warn + 5f)
        val band =
            when {
                held >= alert -> "alert"
                held >= warn -> "warn"
                else -> "ok"
            }
        val sideLabel = if (s == "left") "Izq" else "Der"
        return State(
            side = s,
            heldSec = held,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label =
                when (band) {
                    "alert", "warn" -> "Inter · $sideLabel ${held.toInt()}s"
                    else -> "Inter · $sideLabel"
                },
        )
    }

    fun voicePhrase(st: State): String {
        val side =
            when (st.side) {
                "left" -> "izquierda"
                "right" -> "derecha"
                else -> ""
            }
        return when (st.band) {
            "alert" ->
                "Atención. Intermitente $side olvidado. Llevas ${st.heldSec.toInt()} segundos."
            "warn" ->
                "Cuidado. El intermitente $side sigue encendido."
            else -> "Intermitente $side activo."
        }
    }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFFE11D48
            "warn" -> 0xFFF59E0B
            "ok" -> 0xFFEAB308
            else -> 0xFF94A3B8
        }
}
