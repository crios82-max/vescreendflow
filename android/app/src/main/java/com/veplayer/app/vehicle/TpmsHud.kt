package com.veplayer.app.vehicle

/**
 * Per-wheel TPMS HUD: low pressure bands for FL/FR/RL/RR.
 */
object TpmsHud {
    enum class Wheel(val id: String, val label: String) {
        FL("fl", "FL"),
        FR("fr", "FR"),
        RL("rl", "RL"),
        RR("rr", "RR"),
    }

    data class WheelReading(
        val wheel: Wheel,
        val psi: Float,
        /** ok | warn | alert */
        val band: String,
    )

    data class State(
        val flPsi: Float? = null,
        val frPsi: Float? = null,
        val rlPsi: Float? = null,
        val rrPsi: Float? = null,
        val wheels: List<WheelReading> = emptyList(),
        val lowWheels: List<String> = emptyList(),
        val minPsi: Float? = null,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
        val detail: String = "",
    )

    fun evaluate(
        fl: Float?,
        fr: Float?,
        rl: Float?,
        rr: Float?,
        warnPsi: Float = 28f,
        alertPsi: Float = 24f,
    ): State {
        val warn = warnPsi.coerceIn(15f, 40f)
        val alert = alertPsi.coerceIn(10f, warn - 0.5f)
        val raw =
            listOf(
                Wheel.FL to fl,
                Wheel.FR to fr,
                Wheel.RL to rl,
                Wheel.RR to rr,
            )
        if (raw.all { it.second == null }) {
            return State(band = "idle", label = "")
        }
        val readings =
            raw.mapNotNull { (wheel, psi) ->
                if (psi == null) return@mapNotNull null
                val p = psi.coerceAtLeast(0f)
                val band =
                    when {
                        p < alert -> "alert"
                        p < warn -> "warn"
                        else -> "ok"
                    }
                WheelReading(wheel, p, band)
            }
        val low = readings.filter { it.band == "warn" || it.band == "alert" }
        val band =
            when {
                readings.any { it.band == "alert" } -> "alert"
                readings.any { it.band == "warn" } -> "warn"
                else -> "ok"
            }
        val minPsi = readings.minOfOrNull { it.psi }
        val lowIds = low.map { it.wheel.label }
        val detail =
            readings.joinToString(" · ") { "${it.wheel.label} ${it.psi.toInt()}" }
        val label =
            when (band) {
                "alert", "warn" ->
                    if (lowIds.isEmpty()) {
                        "TPMS bajo"
                    } else {
                        "TPMS ${lowIds.joinToString("·")} · ${minPsi?.toInt() ?: "—"} psi"
                    }
                else -> "TPMS ${minPsi?.toInt() ?: "—"}"
            }
        return State(
            flPsi = fl,
            frPsi = fr,
            rlPsi = rl,
            rrPsi = rr,
            wheels = readings,
            lowWheels = lowIds,
            minPsi = minPsi,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = label,
            detail = detail,
        )
    }

    fun voicePhrase(st: State): String {
        val which =
            if (st.lowWheels.isEmpty()) {
                "neumáticos"
            } else {
                st.lowWheels.joinToString(", ")
            }
        val psi = st.minPsi?.toInt()?.let { "$it psi" } ?: "baja"
        return when (st.band) {
            "alert" -> "Atención. Presión crítica en $which. $psi."
            "warn" -> "Cuidado. Presión baja en $which. $psi."
            else -> "Presión de neumáticos normal."
        }
    }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFFE11D48
            "warn" -> 0xFFF97316
            "ok" -> 0xFF14B8A6
            else -> 0xFF94A3B8
        }

    fun toJsonMap(st: State): Map<String, Any?> =
        mapOf(
            "fl_psi" to st.flPsi?.toDouble(),
            "fr_psi" to st.frPsi?.toDouble(),
            "rl_psi" to st.rlPsi?.toDouble(),
            "rr_psi" to st.rrPsi?.toDouble(),
            "low" to st.showWarn,
            "band" to st.band,
            "low_wheels" to st.lowWheels,
            "min_psi" to st.minPsi?.toDouble(),
        )
}
