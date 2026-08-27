package com.veplayer.app.vehicle

/**
 * High throttle / WOT bands (OBD PID 0111).
 */
object HighThrottle {
    data class State(
        val throttlePct: Float? = null,
        val highForSec: Float = 0f,
        val speedKmh: Float = 0f,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        throttlePct: Float?,
        speedKmh: Float = 0f,
        highForSec: Float = 0f,
        warnPct: Float = 70f,
        alertPct: Float = 85f,
        alertHoldSec: Float = 8f,
        speedMinKmh: Float = 20f,
    ): State {
        if (throttlePct == null) {
            return State(band = "idle", label = "")
        }
        val thr = throttlePct.coerceIn(0f, 100f)
        val speed = speedKmh.coerceAtLeast(0f)
        val warn = warnPct.coerceIn(40f, 95f)
        val alert = alertPct.coerceAtLeast(warn + 5f).coerceAtMost(100f)
        val hold = alertHoldSec.coerceIn(2f, 60f)
        val minSpd = speedMinKmh.coerceIn(0f, 60f)

        if (speed < minSpd) {
            return State(
                throttlePct = thr,
                speedKmh = speed,
                band = "ok",
                label = if (thr >= 40f) "Acel · ${thr.toInt()}%" else "",
            )
        }

        val high = thr >= warn
        val held = highForSec.coerceAtLeast(0f)
        val band =
            when {
                thr >= alert -> "alert"
                high && held >= hold -> "alert"
                high -> "warn"
                else -> "ok"
            }
        return State(
            throttlePct = thr,
            highForSec = if (high) held else 0f,
            speedKmh = speed,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "Acel · ${thr.toInt()}%",
        )
    }

    fun voicePhrase(st: State): String {
        val p = st.throttlePct?.toInt()?.let { "$it por ciento" } ?: "alta"
        return when (st.band) {
            "alert" -> "Atención. Aceleración máxima prolongada. $p. Modera el acelerador."
            "warn" -> "Cuidado. Acelerador muy abierto. $p."
            else -> "Acelerador a $p."
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
            "throttle_pct" to st.throttlePct?.toDouble(),
            "high_for_sec" to st.highForSec.toDouble(),
            "speed_kmh" to st.speedKmh.toDouble(),
            "band" to st.band,
            "show_warn" to st.showWarn,
        )
}
