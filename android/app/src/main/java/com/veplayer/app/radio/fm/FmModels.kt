package com.veplayer.app.radio.fm

/** FM band region (channel spacing). */
enum class FmRegion(
    val id: String,
    val label: String,
    val minKhz: Int,
    val maxKhz: Int,
    val stepKhz: Int,
) {
    ITU_2("itu2", "Américas (200 kHz)", 87_500, 108_000, 200),
    ITU_1("itu1", "Europa/Asia (100 kHz)", 87_500, 108_000, 100),
    ;

    companion object {
        fun fromId(id: String): FmRegion =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ITU_2
    }
}

data class FmStation(
    val id: String,
    val name: String,
    val city: String,
    val genre: String,
    /** Frequency in kHz, e.g. 95_500 = 95.5 MHz */
    val freqKhz: Int,
) {
    val freqMhzLabel: String
        get() = FmFreq.formatMhz(freqKhz)
}

data class FmTunerState(
    val powered: Boolean = false,
    val freqKhz: Int = 95_500,
    val stereo: Boolean = true,
    val signalPct: Int = 0,
    val rdsPs: String = "",
    val rdsRt: String = "",
    val backend: String = "off",
    val seeking: Boolean = false,
    val status: String = "FM off",
)

object FmFreq {
    fun formatMhz(khz: Int): String {
        val mhz = khz / 1000.0
        return if (khz % 100 == 0) {
            String.format("%.1f", mhz)
        } else {
            String.format("%.2f", mhz)
        } + " MHz"
    }

    fun snap(
        khz: Int,
        region: FmRegion,
    ): Int {
        val clamped = khz.coerceIn(region.minKhz, region.maxKhz)
        val steps = ((clamped - region.minKhz + region.stepKhz / 2) / region.stepKhz)
        return (region.minKhz + steps * region.stepKhz).coerceIn(region.minKhz, region.maxKhz)
    }

    fun step(
        khz: Int,
        region: FmRegion,
        up: Boolean,
    ): Int {
        val delta = if (up) region.stepKhz else -region.stepKhz
        var next = snap(khz, region) + delta
        if (next > region.maxKhz) next = region.minKhz
        if (next < region.minKhz) next = region.maxKhz
        return next
    }
}

interface FmTuner {
    val name: String

    fun open(): Boolean

    fun close()

    fun setFrequency(khz: Int): Boolean

    fun seek(up: Boolean): Int?

    fun current(): FmTunerState
}
