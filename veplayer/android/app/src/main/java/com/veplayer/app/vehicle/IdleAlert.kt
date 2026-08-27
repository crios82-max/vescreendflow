package com.veplayer.app.vehicle

/**
 * Engine-idle detection math (stopped + ignition on).
 * Shared with `veplayer/scripts/idle-alert-smoke.mjs`.
 */
object IdleAlert {
    data class State(
        val speedKmh: Float,
        val ignitionOn: Boolean,
        /** Seconds stopped with ignition on (0 if moving / ign off). */
        val idleForSec: Float,
        /** moving | off | idle | warn | alert */
        val band: String,
        val showWarn: Boolean,
    )

    fun isIgnitionOn(ignition: IgnitionState): Boolean =
        ignition == IgnitionState.ON ||
            ignition == IgnitionState.ACC ||
            ignition == IgnitionState.START

    fun isStopped(
        speedKmh: Float,
        speedMaxKmh: Float = 1.5f,
    ): Boolean = speedKmh <= speedMaxKmh

    /**
     * Pure band evaluation given already-accumulated idle seconds.
     */
    fun evaluate(
        speedKmh: Float,
        ignitionOn: Boolean,
        idleForSec: Float,
        warnSec: Float = 120f,
        alertSec: Float = 300f,
        speedMaxKmh: Float = 1.5f,
    ): State {
        val speed = speedKmh.coerceAtLeast(0f)
        if (!ignitionOn) {
            return State(speed, false, 0f, "off", false)
        }
        if (!isStopped(speed, speedMaxKmh)) {
            return State(speed, true, 0f, "moving", false)
        }
        val idle = idleForSec.coerceAtLeast(0f)
        val band =
            when {
                idle >= alertSec -> "alert"
                idle >= warnSec -> "warn"
                else -> "idle"
            }
        return State(
            speedKmh = speed,
            ignitionOn = true,
            idleForSec = idle,
            band = band,
            showWarn = band == "warn" || band == "alert",
        )
    }

    fun voicePhrase(state: State): String {
        val mins = (state.idleForSec / 60f).toInt().coerceAtLeast(0)
        val secs = (state.idleForSec % 60f).toInt()
        val dur =
            if (mins > 0) {
                "$mins minutos"
            } else {
                "$secs segundos"
            }
        return when (state.band) {
            "alert" -> "Motor en ralentí prolongado. Llevas $dur detenido con el motor encendido."
            "warn" -> "Vehículo en ralentí. Llevas $dur detenido."
            else -> "Vehículo detenido."
        }
    }

    fun labelLine(state: State): String {
        val m = (state.idleForSec / 60f).toInt()
        val s = (state.idleForSec % 60f).toInt()
        val clock = "%d:%02d".format(m, s)
        return when (state.band) {
            "alert" -> "IDLE · $clock · ALERT"
            "warn" -> "IDLE · $clock · WARN"
            "idle" -> "IDLE · $clock"
            "off" -> "IGN OFF"
            else -> ""
        }
    }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFFE11D48
            "warn" -> 0xFFF59E0B
            "idle" -> 0xFF94A3B8
            else -> 0xFF64748B
        }
}
