package com.veplayer.app.vehicle

/**
 * Sudden fuel drop (theft / leak): large % drop in a short window.
 */
object SuddenFuelDrop {
    data class State(
        val fuelPct: Float? = null,
        /** Positive = decrease within window. */
        val dropPct: Float = 0f,
        val windowSec: Float = 60f,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        fuelPct: Float?,
        dropPct: Float,
        warnPct: Float = 8f,
        alertPct: Float = 15f,
        windowSec: Float = 60f,
    ): State {
        if (fuelPct == null) {
            return State(band = "idle", label = "")
        }
        val drop = dropPct.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(2f, 50f)
        val alert = alertPct.coerceAtLeast(warn + 1f)
        val band =
            when {
                drop >= alert -> "alert"
                drop >= warn -> "warn"
                else -> "ok"
            }
        return State(
            fuelPct = fuelPct,
            dropPct = drop,
            windowSec = windowSec,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label =
                if (band == "ok") {
                    "${fuelPct.toInt()}%"
                } else {
                    "−${drop.toInt()}% · ${fuelPct.toInt()}%"
                },
        )
    }

    fun voicePhrase(st: State): String {
        val drop = st.dropPct.toInt()
        val fuel = st.fuelPct?.toInt()?.let { "$it por ciento" } ?: "desconocido"
        return when (st.band) {
            "alert" ->
                "Atención. Caída brusca de combustible. Menos $drop por ciento. Nivel actual $fuel."
            "warn" ->
                "Cuidado. Combustible bajando rápido. Menos $drop por ciento."
            else -> "Combustible a $fuel."
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
