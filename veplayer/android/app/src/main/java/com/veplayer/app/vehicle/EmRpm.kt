package com.veplayer.app.vehicle

/** Electric motor A RPM (OBD PID 01CC bytes A/B). High RPM = stress. */
object EmRpm {
    data class State(val rpm: Float? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(rpm: Float?, warnRpm: Float = 12000f, alertRpm: Float = 16000f): State {
        if (rpm == null) return State(band = "idle", label = "")
        val r = rpm.coerceIn(0f, 100000f)
        val warn = warnRpm.coerceIn(3000f, 50000f)
        val alert = alertRpm.coerceAtLeast(warn + 500f)
        val band = when { r >= alert -> "alert"; r >= warn -> "warn"; else -> "ok" }
        return State(rpm = r, band = band, showWarn = band != "ok", label = "EmRpm · ${r.toInt()}")
    }

    fun voicePhrase(st: State): String {
        val r = st.rpm?.toInt()?.toString() ?: "elevadas"
        return when (st.band) {
            "alert" -> "Atención. RPM motor eléctrico críticas. $r."
            "warn" -> "Cuidado. RPM motor eléctrico altas. $r."
            else -> "RPM motor eléctrico a $r."
        }
    }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("rpm" to st.rpm?.toDouble(), "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
