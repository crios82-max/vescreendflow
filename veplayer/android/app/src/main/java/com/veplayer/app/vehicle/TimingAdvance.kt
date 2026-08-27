package com.veplayer.app.vehicle

/**
 * Ignition timing advance bands (OBD PID 010E), degrees.
 */
object TimingAdvance {
    data class State(
        val timingDeg: Float? = null,
        val speedKmh: Float = 0f,
        val rpm: Float? = null,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        timingDeg: Float?,
        speedKmh: Float = 0f,
        rpm: Float? = null,
        warnDeg: Float = 38f,
        alertDeg: Float = 45f,
        speedMinKmh: Float = 20f,
        rpmMin: Float = 800f,
    ): State {
        if (timingDeg == null) {
            return State(band = "idle", label = "")
        }
        val deg = timingDeg.coerceIn(-64f, 64f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnDeg.coerceIn(10f, 60f)
        val alert = alertDeg.coerceAtLeast(warn + 3f).coerceAtMost(64f)
        val minSpd = speedMinKmh.coerceIn(0f, 60f)
        val minRpm = rpmMin.coerceIn(400f, 3000f)
        val rpmOk = rpm == null || rpm >= minRpm

        if (speed < minSpd || !rpmOk) {
            return State(
                timingDeg = deg,
                speedKmh = speed,
                rpm = rpm,
                band = "ok",
                label = if (deg >= 5f) "Timing · ${deg.toInt()}°" else "",
            )
        }

        val band =
            when {
                deg >= alert -> "alert"
                deg >= warn -> "warn"
                else -> "ok"
            }
        return State(
            timingDeg = deg,
            speedKmh = speed,
            rpm = rpm,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "Timing · ${deg.toInt()}°",
        )
    }

    fun voicePhrase(st: State): String {
        val d = st.timingDeg?.toInt()?.let { "$it grados" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Adelanto de encendido crítico. $d. Reduce carga."
            "warn" -> "Cuidado. Adelanto de encendido alto. $d."
            else -> "Adelanto de encendido a $d."
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
            "timing_deg" to st.timingDeg?.toDouble(),
            "speed_kmh" to st.speedKmh.toDouble(),
            "rpm" to st.rpm?.toDouble(),
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
