package com.veplayer.app.vehicle

/** Commanded equivalence ratio (OBD PID 0144), lambda ~1.0 = stoichiometric. */
object EquivRatio {
    data class State(
        val ratio: Float? = null,
        val speedKmh: Float = 0f,
        val rpm: Float? = null,
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        ratio: Float?,
        speedKmh: Float = 0f,
        rpm: Float? = null,
        warnLow: Float = 0.88f,
        alertLow: Float = 0.80f,
        warnHigh: Float = 1.12f,
        alertHigh: Float = 1.20f,
        speedMinKmh: Float = 20f,
        rpmMin: Float = 800f,
    ): State {
        if (ratio == null) return State(band = "idle", label = "")
        val r = ratio.coerceIn(0.1f, 2.5f)
        val speed = speedKmh.coerceAtLeast(0f)
        val wLo = warnLow.coerceIn(0.5f, 1.0f)
        val aLo = alertLow.coerceIn(0.4f, wLo - 0.02f)
        val wHi = warnHigh.coerceIn(1.0f, 1.5f)
        val aHi = alertHigh.coerceAtLeast(wHi + 0.02f).coerceAtMost(2.0f)
        val minSpd = speedMinKmh.coerceIn(0f, 60f)
        val rpmOk = rpm == null || rpm >= rpmMin.coerceIn(400f, 3000f)
        if (speed < minSpd || !rpmOk) {
            return State(
                ratio = r,
                speedKmh = speed,
                rpm = rpm,
                band = "ok",
                label = if (r in 0.85f..1.15f) "Lambda · ${fmt(r)}" else "",
            )
        }
        val band =
            when {
                r <= aLo || r >= aHi -> "alert"
                r <= wLo || r >= wHi -> "warn"
                else -> "ok"
            }
        return State(
            ratio = r,
            speedKmh = speed,
            rpm = rpm,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "Lambda · ${fmt(r)}",
        )
    }

    private fun fmt(r: Float): String = String.format("%.2f", r)

    fun voicePhrase(st: State): String {
        val v = st.ratio?.let { fmt(it) } ?: "anómala"
        return when (st.band) {
            "alert" -> "Atención. Mezcla crítica. Lambda $v. Revisa motor."
            "warn" -> "Cuidado. Mezcla fuera de rango. Lambda $v."
            else -> "Lambda a $v."
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
            "ratio" to st.ratio?.toDouble(),
            "speed_kmh" to st.speedKmh.toDouble(),
            "rpm" to st.rpm?.toDouble(),
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
