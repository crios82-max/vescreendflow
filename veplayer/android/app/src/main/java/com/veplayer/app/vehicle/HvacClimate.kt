package com.veplayer.app.vehicle

/**
 * Cabin climate math — comfort vs heat/cool delta to target.
 */
object HvacClimate {
    data class State(
        val cabinC: Float? = null,
        val targetC: Float? = null,
        val acOn: Boolean = false,
        val fanLevel: Int = 0,
        /** idle | comfort | cool | heat | unknown */
        val band: String = "idle",
        val deltaC: Float = 0f,
        val label: String = "",
        val showPanel: Boolean = false,
    )

    fun evaluate(
        cabinC: Float?,
        targetC: Float?,
        acOn: Boolean,
        fanLevel: Int,
        comfortDeltaC: Float = 2.5f,
    ): State {
        if (cabinC == null && targetC == null) {
            return State(acOn = acOn, fanLevel = fanLevel, band = "idle", label = "")
        }
        val cabin = cabinC
        val target = targetC ?: cabinC
        val delta =
            if (cabin != null && target != null) {
                cabin - target
            } else {
                0f
            }
        val band =
            when {
                cabin == null || target == null -> "unknown"
                kotlin.math.abs(delta) <= comfortDeltaC -> "comfort"
                delta > comfortDeltaC -> "heat"
                else -> "cool"
            }
        val cabinTxt = cabin?.let { "${it.toInt()}°" } ?: "—"
        val targetTxt = target?.let { "${it.toInt()}°" } ?: "—"
        val ac = if (acOn) "AC" else "AC off"
        val fan = if (fanLevel > 0) "fan $fanLevel" else "fan off"
        return State(
            cabinC = cabin,
            targetC = target,
            acOn = acOn,
            fanLevel = fanLevel.coerceIn(0, 7),
            band = band,
            deltaC = delta,
            label = "$cabinTxt → $targetTxt · $ac · $fan",
            showPanel = true,
        )
    }

    fun voicePhrase(st: State): String {
        val cabin = st.cabinC?.toInt()?.let { "$it grados" } ?: "desconocida"
        val target = st.targetC?.toInt()?.let { "$it grados" } ?: "objetivo"
        return when (st.band) {
            "heat" -> "Cabina caliente. $cabin. Objetivo $target."
            "cool" -> "Cabina fresca. $cabin. Objetivo $target."
            "comfort" -> "Clima en confort. $cabin."
            else -> "Clima. Cabina $cabin."
        }
    }

    fun accentArgb(band: String): Long =
        when (band) {
            "heat" -> 0xFFF97316
            "cool" -> 0xFF38BDF8
            "comfort" -> 0xFF14B8A6
            else -> 0xFF94A3B8
        }

    fun dockLabel(st: State): String {
        val cabin = st.cabinC?.let { "%.0f°".format(it) } ?: "—"
        return if (st.acOn) "$cabin AC" else cabin
    }
}
