package com.veplayer.app.vehicle

/** Fuel type (OBD PID 0151), SAE enum code. */
object FuelType {
    data class State(
        val typeCode: Int? = null,
        val speedKmh: Float = 0f,
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun labelFor(code: Int): String =
        when (code) {
            1 -> "Gas"
            2 -> "MetOH"
            3 -> "Eth"
            4 -> "Diesel"
            5 -> "LPG"
            6 -> "CNG"
            7 -> "Prop"
            8 -> "EV"
            9 -> "BiGas"
            10 -> "BiMet"
            11 -> "BiEth"
            else -> "T$code"
        }

    fun evaluate(typeCode: Int?, speedKmh: Float = 0f, expectedCode: Int = 1, speedMinKmh: Float = 5f): State {
        if (typeCode == null || typeCode == 0) return State(band = "idle", label = "")
        val code = typeCode.coerceIn(0, 255)
        val speed = speedKmh.coerceAtLeast(0f)
        val label = "Fuel · ${labelFor(code)}"
        if (speed < speedMinKmh.coerceIn(0f, 60f)) {
            return State(typeCode = code, speedKmh = speed, band = "ok", label = label)
        }
        val band = if (code != expectedCode.coerceIn(1, 20)) "alert" else "ok"
        return State(typeCode = code, speedKmh = speed, band = band, showWarn = band != "ok", label = label)
    }

    fun voicePhrase(st: State): String {
        val t = st.typeCode?.let { labelFor(it) } ?: "desconocido"
        return when (st.band) {
            "alert" -> "Atención. Tipo de combustible incorrecto. $t. Verifica surtidor."
            else -> "Combustible $t."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> =
        mapOf("type_code" to st.typeCode, "speed_kmh" to st.speedKmh.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
