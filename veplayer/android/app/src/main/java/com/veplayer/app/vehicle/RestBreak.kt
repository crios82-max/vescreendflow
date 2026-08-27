package com.veplayer.app.vehicle

/**
 * Continuous driving rest-break reminder (resets after a stop of restResetSec).
 */
object RestBreak {
    data class State(
        /** Seconds continuously driving (speed ≥ min). */
        val drivingSec: Float = 0f,
        /** Seconds currently stopped (speed &lt; min). */
        val stoppedSec: Float = 0f,
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
            else -> "%dm".format(m.coerceAtLeast(0))
        }
    }

    fun evaluate(
        drivingSec: Float,
        stoppedSec: Float = 0f,
        warnSec: Float = 2f * 3600f,
        alertSec: Float = 2.5f * 3600f,
    ): State {
        val drive = drivingSec.coerceAtLeast(0f)
        val stop = stoppedSec.coerceAtLeast(0f)
        if (drive <= 0f && stop <= 0f) {
            return State(band = "idle", label = "")
        }
        val warn = warnSec.coerceAtLeast(600f)
        val alert = alertSec.coerceAtLeast(warn + 60f)
        val band =
            when {
                drive >= alert -> "alert"
                drive >= warn -> "warn"
                else -> "ok"
            }
        return State(
            drivingSec = drive,
            stoppedSec = stop,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label =
                if (band == "ok") {
                    "Conduciendo · ${formatDuration(drive)}"
                } else {
                    "Descanso · ${formatDuration(drive)}"
                },
        )
    }

    fun voicePhrase(st: State): String {
        val hLabel =
            if (st.drivingSec >= 3600f) {
                "%.1f horas".format(st.drivingSec / 3600f)
            } else {
                "${(st.drivingSec / 60f).toInt()} minutos"
            }
        return when (st.band) {
            "alert" ->
                "Atención. Llevas $hLabel al volante sin pausa. Es hora de un descanso."
            "warn" ->
                "Cuidado. Llevas $hLabel conduciendo. Toma una pausa pronto."
            else -> "Conducción continua. $hLabel."
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
