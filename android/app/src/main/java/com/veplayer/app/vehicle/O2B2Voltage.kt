package com.veplayer.app.vehicle

/** O2 sensor voltage B1S2 (OBD PID 014B), volts — stuck lean/rich. */
object O2B2Voltage {
    data class State(
        val o2Volts: Float? = null,
        val speedKmh: Float = 0f,
        val rpm: Float? = null,
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
        if (o2Volts == null) return State(band = "idle", label = "")
        val base =
            O2Voltage.evaluate(
                o2Volts = o2Volts,
                speedKmh = speedKmh,
                rpm = rpm,
                warnLowV = warnLowV,
                alertLowV = alertLowV,
                warnHighV = warnHighV,
                alertHighV = alertHighV,
                speedMinKmh = speedMinKmh,
                rpmMin = rpmMin,
            )
        val label =
            when {
                base.label.isBlank() -> ""
                base.label.startsWith("O2 ·") -> "O2B2 · ${base.label.removePrefix("O2 · ")}"
                else -> base.label.replace("O2", "O2B2")
            }
        return State(
            o2Volts = base.o2Volts,
            speedKmh = base.speedKmh,
            rpm = base.rpm,
            band = base.band,
            showWarn = base.showWarn,
            label = label,
        )
    }

    fun voicePhrase(st: State): String {
        val v = st.o2Volts?.let { String.format("%.2f", it) } ?: "anómala"
        return when (st.band) {
            "alert" ->
                when {
                    (st.o2Volts ?: 1f) <= 0.08f ->
                        "Atención. Sensor O2 banco 1 sensor 2 muy bajo. $v voltios."
                    else -> "Atención. Sensor O2 banco 1 sensor 2 muy alto. $v voltios."
                }
            "warn" -> "Cuidado. Sensor O2 B1S2 fuera de rango. $v voltios."
            else -> "Sensor O2 B1S2 a $v voltios."
        }
    }

    fun accentArgb(band: String): Long = O2Voltage.accentArgb(band)

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
