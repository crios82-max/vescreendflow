package com.veplayer.app.vehicle

/**
 * Fuel / SOC / range HUD math.
 * Shared with `veplayer/scripts/fuel-hud-smoke.mjs`.
 */
object FuelRangeHud {
    data class State(
        /** fuel | soc | none */
        val kind: String,
        val levelPct: Float?,
        val rangeKm: Float?,
        /** ok | near | low */
        val band: String,
        val showWarn: Boolean,
        /** level | range | both | none */
        val reason: String,
    )

    fun evaluate(
        fuelPct: Float?,
        socPct: Float?,
        rangeKm: Float?,
        warnPct: Float = 20f,
        criticalPct: Float = 10f,
        warnRangeKm: Float = 40f,
        criticalRangeKm: Float = 20f,
    ): State {
        val kind =
            when {
                fuelPct != null -> "fuel"
                socPct != null -> "soc"
                else -> "none"
            }
        val level =
            when (kind) {
                "fuel" -> fuelPct!!.coerceIn(0f, 100f)
                "soc" -> socPct!!.coerceIn(0f, 100f)
                else -> null
            }
        val range = rangeKm?.coerceAtLeast(0f)

        var levelBand = "ok"
        if (level != null) {
            levelBand =
                when {
                    level <= criticalPct -> "low"
                    level <= warnPct -> "near"
                    else -> "ok"
                }
        }
        var rangeBand = "ok"
        if (range != null) {
            rangeBand =
                when {
                    range <= criticalRangeKm -> "low"
                    range <= warnRangeKm -> "near"
                    else -> "ok"
                }
        }

        val band =
            when {
                levelBand == "low" || rangeBand == "low" -> "low"
                levelBand == "near" || rangeBand == "near" -> "near"
                else -> "ok"
            }
        val reason =
            when {
                levelBand != "ok" && rangeBand != "ok" -> "both"
                levelBand != "ok" -> "level"
                rangeBand != "ok" -> "range"
                else -> "none"
            }
        return State(
            kind = kind,
            levelPct = level,
            rangeKm = range,
            band = band,
            showWarn = band == "low",
            reason = reason,
        )
    }

    fun voicePhrase(state: State): String {
        val pct = state.levelPct?.toInt()
        val rng = state.rangeKm?.toInt()
        val label = if (state.kind == "fuel") "combustible" else "batería"
        return when {
            state.band == "low" && state.reason == "both" && pct != null && rng != null ->
                "Nivel crítico de $label: $pct por ciento. Autonomía $rng kilómetros."
            state.band == "low" && state.reason == "range" && rng != null ->
                "Autonomía crítica: $rng kilómetros."
            state.band == "low" && pct != null ->
                "Nivel crítico de $label: $pct por ciento."
            state.band == "near" && pct != null ->
                "Nivel bajo de $label: $pct por ciento."
            state.band == "near" && rng != null ->
                "Autonomía baja: $rng kilómetros."
            pct != null -> "$label $pct por ciento."
            else -> "Energía del vehículo."
        }
    }

    fun labelLine(state: State): String {
        val pct = state.levelPct?.toInt()?.toString() ?: "—"
        val rng = state.rangeKm?.toInt()?.toString() ?: "—"
        val prefix = if (state.kind == "fuel") "Fuel" else "SOC"
        val band =
            when (state.band) {
                "low" -> "LOW"
                "near" -> "NEAR"
                else -> ""
            }
        return buildString {
            append("$prefix $pct% · rango $rng km")
            if (band.isNotEmpty()) append(" · $band")
        }
    }

    fun accentArgb(band: String): Long =
        when (band) {
            "low" -> 0xFFE11D48
            "near" -> 0xFFF59E0B
            else -> 0xFF94A3B8
        }
}
