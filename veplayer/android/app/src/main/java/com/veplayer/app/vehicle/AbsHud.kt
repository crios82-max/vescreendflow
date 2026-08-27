package com.veplayer.app.vehicle

/**
 * ABS / ESC intervention HUD — sustained active + event burst.
 */
object AbsHud {
    data class State(
        val active: Boolean = false,
        val activeForSec: Float = 0f,
        val events: Int = 0,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        active: Boolean,
        activeForSec: Float = 0f,
        events: Int = 0,
        warnSec: Float = 0.5f,
        alertSec: Float = 2f,
        alertEvents: Int = 3,
    ): State {
        val held = activeForSec.coerceAtLeast(0f)
        val n = events.coerceAtLeast(0)
        val warn = warnSec.coerceIn(0.2f, 5f)
        val alert = alertSec.coerceAtLeast(warn + 0.3f)
        val nAlert = alertEvents.coerceIn(2, 20)

        if (!active && n <= 0 && held <= 0f) {
            return State(band = "idle", label = "")
        }

        val band =
            when {
                (active && held >= alert) || n >= nAlert -> "alert"
                active && held >= warn -> "warn"
                active -> "ok"
                n > 0 -> "ok"
                else -> "idle"
            }
        val showWarn = band == "warn" || band == "alert"
        val label =
            when {
                showWarn && n > 0 -> "ABS · ${held.coerceAtLeast(0.1f).let { "%.1f".format(it) }}s · ×$n"
                showWarn -> "ABS · ${"%.1f".format(held)}s"
                active -> "ABS"
                n > 0 -> "ABS · ×$n"
                else -> ""
            }
        return State(
            active = active,
            activeForSec = held,
            events = n,
            band = band,
            showWarn = showWarn,
            label = label,
        )
    }

    fun voicePhrase(st: State): String =
        when (st.band) {
            "alert" ->
                if (st.events >= 3) {
                    "Atención. ABS intervenido varias veces. Reduce velocidad."
                } else {
                    "Atención. ABS activo de forma prolongada. Reduce velocidad."
                }
            "warn" -> "Cuidado. Sistema ABS activo."
            else -> "ABS."
        }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFFE11D48
            "warn" -> 0xFFF59E0B
            "ok" -> 0xFFEAB308
            else -> 0xFF94A3B8
        }

    fun toJsonMap(st: State): Map<String, Any?> =
        mapOf(
            "active" to st.active,
            "active_for_sec" to st.activeForSec.toDouble(),
            "events" to st.events,
            "band" to st.band,
            "show_warn" to st.showWarn,
        )
}
