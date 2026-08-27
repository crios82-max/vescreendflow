package com.veplayer.app.vehicle

/**
 * Engine run time since start (OBD PID 011F) — long continuous run → warn / alert.
 */
object EngineRuntime {
    data class State(
        val runtimeSec: Int? = null,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun formatDuration(sec: Int): String {
        val s = sec.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        return when {
            h > 0 -> "%dh %02dm".format(h, m)
            else -> "%dm".format(m.coerceAtLeast(0))
        }
    }

    fun evaluate(
        runtimeSec: Int?,
        warnSec: Float = 2f * 3600f,
        alertSec: Float = 4f * 3600f,
    ): State {
        if (runtimeSec == null) {
            return State(band = "idle", label = "")
        }
        val rt = runtimeSec.coerceAtLeast(0)
        val warn = warnSec.coerceAtLeast(600f)
        val alert = alertSec.coerceAtLeast(warn + 60f)
        val band =
            when {
                rt >= alert -> "alert"
                rt >= warn -> "warn"
                else -> "ok"
            }
        return State(
            runtimeSec = rt,
            band = band,
            showWarn = band == "warn" || band == "alert",
            label = "Motor · ${formatDuration(rt)}",
        )
    }

    fun voicePhrase(st: State): String {
        val sec = st.runtimeSec ?: 0
        val hLabel =
            if (sec >= 3600) {
                "%.1f horas".format(sec / 3600f)
            } else {
                "${(sec / 60).coerceAtLeast(1)} minutos"
            }
        return when (st.band) {
            "alert" ->
                "Atención. Motor encendido $hLabel. Apaga el motor si estás parado."
            "warn" ->
                "Cuidado. El motor lleva $hLabel en marcha. Considera apagarlo en parado."
            else -> "Tiempo de motor. $hLabel."
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
            "runtime_sec" to st.runtimeSec,
            "band" to st.band,
            "show_warn" to st.showWarn,
            "label" to st.label,
        )
}
