package com.veplayer.app.vehicle

/** Battery capacity calculation ready (OBD PID 01D8 byte A bit0). Alert when not ready. */
object BcapReady {
    data class State(val ready: Boolean? = null, val band: String = "idle", val showWarn: Boolean = false, val label: String = "")

    fun evaluate(ready: Boolean?): State {
        if (ready == null) return State(band = "idle", label = "")
        return if (ready) {
            State(ready = true, band = "ok", label = "Bcap · Ready")
        } else {
            State(ready = false, band = "warn", showWarn = true, label = "Bcap · NotReady")
        }
    }

    fun voicePhrase(st: State): String =
        when (st.band) {
            "warn", "alert" -> "Atención. Cálculo de capacidad de batería no listo."
            else -> "Cálculo de capacidad de batería listo."
        }

    fun accentArgb(band: String): Long = when (band) { "alert" -> 0xFFE11D48; "warn" -> 0xFFF97316; "ok" -> 0xFF14B8A6; else -> 0xFF94A3B8 }

    fun toJsonMap(st: State): Map<String, Any?> = mapOf("ready" to st.ready, "band" to st.band, "show_warn" to st.showWarn, "label" to st.label)
}
