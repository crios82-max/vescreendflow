package com.veplayer.app.vehicle

/**
 * O2 sensor voltage bands (OBD PID 014A B1S1), volts — stuck lean/rich.
 */
object O2Voltage {
    data class State(
        val o2Volts: Float? = null,
        val speedKmh: Float = 0f,
        val rpm: Float? = null,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        o2Volts: Float?,
        speedKmh: Float = 0f,
        rpm: Float? = null,
        warnLowV: Float = 0.10f,
        alertLowV: Float = 0.06f,
        warnHighV: Float = 0.88f,
        alertHighV: Float = 0.95f,
        speedMinKmh: Float = 20f,
        rpmMin: Float = 800f,
    ): State {
        if (o2Volts == null) {
            return State(band = "idle", label = "")
        }
        val v = o2Volts.coerceIn(0f, 1.275f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warnLow = warnLowV.coerceIn(0.02f, 0.5f)
        val alertLow = alertLowV.coerceIn(0.01f, warnLow - 0.02f)
        val warnHigh = warnHighV.coerceIn(0.5f, 1.2f)
        val alertHigh = alertHighV.coerceAtLeast(warnHigh + 0.02f).coerceAtMost(1.275f)
        val minSpd = speedMinKmh.coerceIn(0f, 60f)
        val minRpm = rpmMin.coerceIn(400f, 3000f)
        val rpmOk = rpm == null || rpm >= minRpm

        if (speed < minSpd || !rpmOk) {
            return State(
                o2Volts = v,
                speedKmh = speed,
                rpm = rpm,
                band = "ok",
                label = if (v in 0.15f..0.85f) "O2 · ${fmtV(v)} V" else "",
            )
        }

        val band =
            when {
                v <= alertLow || v >= alertHigh -> "alert"
                v <= warnLow || v >= warnHigh -> "warn"
                else -> "ok"
            }
        return State(
            o2Volts = v,
            speedKmh = speed,
            rpm = rpm,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "O2 · ${fmtV(v)} V",
        )
    }

    private fun fmtV(v: Float): String =
        if (v < 0.1f) String.format("%.2f", v) else String.format("%.2f", v)

    fun voicePhrase(st: State): String {
        val v = st.o2Volts?.let { fmtV(it) } ?: "anómala"
        return when (st.band) {
            "alert" ->
                when {
                    (st.o2Volts ?: 1f) <= 0.08f ->
                        "Atención. Sensor O2 muy bajo. $v voltios. Mezcla pobre o sensor."
                    else -> "Atención. Sensor O2 muy alto. $v voltios. Mezcla rica o sensor."
                }
            "warn" -> "Cuidado. Sensor O2 fuera de rango. $v voltios."
            else -> "Sensor O2 a $v voltios."
        }
    }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFFE11D48
            "warn" -> 0xFFF59E0B
            "ok" -> 0xFF14B8A6
            else -> 0xFF94A3B8
        }

    fun toJsonMap(st: State): Map<String, Any?> =
        mapOf(
            "o2_volts" to st.o2Volts?.toDouble(),
            "speed_kmh" to st.speedKmh.toDouble(),
            "rpm" to st.rpm?.toDouble(),
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
