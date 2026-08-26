package com.veplayer.app.vehicle

/**
 * Speed HUD math — limit badge + overspeed bands.
 * Shared with `veplayer/scripts/speed-hud-smoke.mjs`.
 */
object SpeedHud {
    data class State(
        val speedKmh: Float,
        val limitKmh: Int,
        val overBy: Float,
        /** ok | near | over */
        val band: String,
        val showWarn: Boolean,
    )

    fun evaluate(
        speedKmh: Float,
        limitKmh: Int,
        warnMarginKmh: Float = 5f,
    ): State {
        val limit = limitKmh.coerceIn(10, 160)
        val speed = speedKmh.coerceAtLeast(0f)
        val overBy = speed - limit
        val band =
            when {
                overBy > 0f -> "over"
                overBy > -warnMarginKmh -> "near"
                else -> "ok"
            }
        return State(
            speedKmh = speed,
            limitKmh = limit,
            overBy = overBy,
            band = band,
            showWarn = overBy > 0f,
        )
    }

    fun voicePhrase(state: State): String {
        val lim = state.limitKmh
        val spd = state.speedKmh.toInt()
        return when {
            state.overBy >= 20f -> "Exceso grave de velocidad. Vas a $spd, límite $lim."
            state.overBy > 0f -> "Reduce velocidad. Límite $lim, vas a $spd."
            else -> "Límite de velocidad $lim kilómetros por hora."
        }
    }

    /** ARGB for badge ring / speed digits. */
    fun accentArgb(band: String): Long =
        when (band) {
            "over" -> 0xFFE11D48
            "near" -> 0xFFF59E0B
            else -> 0xFFE8F2EE
        }
}
