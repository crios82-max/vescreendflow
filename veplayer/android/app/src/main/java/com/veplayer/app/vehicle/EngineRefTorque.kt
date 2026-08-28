package com.veplayer.app.vehicle

/** Engine reference torque (OBD PID 0163), Nm. */
object EngineRefTorque {
    data class State(val torqueNm: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(torqueNm: Float?, warnLowNm: Float = 100f, alertLowNm: Float = 80f, warnHighNm: Float = 450f, alertHighNm: Float = 520f): State {
        if (torqueNm == null) return State(band = "idle", label = "")
        val nm = torqueNm.coerceIn(0f, 2000f)
        val wLo = warnLowNm.coerceIn(50f, 300f)
        val aLo = alertLowNm.coerceAtMost(wLo - 10f).coerceAtLeast(40f)
        val wHi = warnHighNm.coerceIn(300f, 800f)
        val aHi = alertHighNm.coerceAtLeast(wHi + 20f).coerceAtMost(1200f)
        val band = when {
            nm <= aLo || nm >= aHi -> "alert"
            nm <= wLo || nm >= wHi -> "warn"
            else -> "ok"
        }
        return State(torqueNm = nm, band = band, showWarn = band != "ok", label = "RefT · ${nm.toInt()}Nm")
    }

    fun voicePhrase(st: State): String {
        val n = st.torqueNm?.toInt()?.let { "$it newton metros" } ?: "anómala"
        return when (st.band) {
            "alert" -> "Atención. Torque referencia crítico. $n."
            "warn" -> "Cuidado. Torque referencia fuera de rango. $n."
            else -> "Torque referencia a $n."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("torque_nm" to st.torqueNm?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
