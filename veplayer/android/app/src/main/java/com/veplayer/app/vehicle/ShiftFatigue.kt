package com.veplayer.app.vehicle

/**
 * Shift duration / driver fatigue bands from open-shift elapsed time.
 */
object ShiftFatigue {
    data class State(
        val open: Boolean = false,
        /** Elapsed seconds on open shift (0 if closed). */
        val durationSec: Float = 0f,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun formatDuration(sec: Float): String {
        val s = sec.coerceAtLeast(0f).toInt()
        val h = s / 3600
        val m = (s % 3600) / 60
        return when {
            h > 0 -> "%dh %02dm".format(h, m)
            else -> "%dm".format(m)
        }
    }

    fun evaluate(
        open: Boolean,
        durationSec: Float,
        warnHours: Float = 4f,
        alertHours: Float = 8f,
    ): State {
        if (!open) {
            return State(open = false, band = "idle", label = "")
        }
        val dur = durationSec.coerceAtLeast(0f)
        val warnSec = (warnHours.coerceAtLeast(0.25f) * 3600f)
        val alertSec = (alertHours.coerceAtLeast(warnHours + 0.25f) * 3600f)
        val band =
            when {
                dur >= alertSec -> "alert"
                dur >= warnSec -> "warn"
                else -> "ok"
            }
        return State(
            open = true,
            durationSec = dur,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = formatDuration(dur),
        )
    }

    fun voicePhrase(st: State): String {
        val hours = (st.durationSec / 3600f)
        val hLabel =
            if (hours >= 1f) {
                "%.1f horas".format(hours)
            } else {
                "${(st.durationSec / 60f).toInt()} minutos"
            }
        return when (st.band) {
            "alert" ->
                "Atención. Turno prolongado. Llevas $hLabel. Es momento de un descanso."
            "warn" ->
                "Cuidado. Llevas $hLabel de turno. Considera una pausa."
            else -> "Turno en curso. $hLabel."
        }
    }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFFE11D48
            "warn" -> 0xFFF59E0B
            "ok" -> 0xFF14B8A6
            else -> 0xFF94A3B8
        }
}
