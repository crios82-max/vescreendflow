package com.veplayer.app.vehicle

/** Hybrid/EV charging mode from OBD PID 019A (0=CSM, 1=CDM, 2=CIM). CIM = warn. */
object HevMode {
    data class State(
        val code: Float? = null,
        val mode: String? = null,
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(code: Float?): State {
        if (code == null) return State(band = "idle", label = "")
        val c = code.toInt().coerceIn(0, 3)
        val mode = when (c) { 0 -> "CSM"; 1 -> "CDM"; 2 -> "CIM"; else -> "?" }
        val band = when (c) { 2 -> "warn"; 0, 1 -> "ok"; else -> "alert" }
        return State(
            code = c.toFloat(),
            mode = mode,
            band = band,
            showWarn = band != "ok",
            label = "HevMode · $mode",
        )
    }

    fun voicePhrase(st: State): String =
        when (st.band) {
            "alert" -> "Atención. Modo híbrido desconocido."
            "warn" -> "Cuidado. Modo de carga en aumento. CIM."
            else -> "Modo híbrido ${st.mode ?: "normal"}."
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
            "code" to st.code?.toDouble(),
            "mode" to st.mode,
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
