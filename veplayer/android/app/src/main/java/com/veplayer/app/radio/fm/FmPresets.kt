package com.veplayer.app.radio.fm

/**
 * Caracas / regional FM presets (hardware tune — no IP stream).
 */
object FmPresets {
    val caracas: List<FmStation> =
        listOf(
            FmStation("fm-955", "La Mega", "Caracas", "Hit", 95_500),
            FmStation("fm-917", "Circuito Éxitos", "Caracas", "Pop", 91_700),
            FmStation("fm-997", "Radio Caracas Radio", "Caracas", "News/Talk", 99_700),
            FmStation("fm-1053", "Hot 105.3", "Caracas", "Urban", 105_300),
            FmStation("fm-883", "Fiesta 88.3", "Caracas", "Tropical", 88_300),
            FmStation("fm-1073", "Planeta", "Caracas", "Rock", 107_300),
            FmStation("fm-933", "RQ 933", "Caracas", "Variety", 93_300),
            FmStation("fm-1015", "Mantarraya", "Caracas", "Alternative", 101_500),
        )

    fun nearest(
        freqKhz: Int,
        list: List<FmStation> = caracas,
    ): FmStation? = list.minByOrNull { kotlin.math.abs(it.freqKhz - freqKhz) }

    fun byId(id: String): FmStation? = caracas.firstOrNull { it.id == id }
}
