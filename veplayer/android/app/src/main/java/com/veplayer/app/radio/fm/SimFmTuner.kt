package com.veplayer.app.radio.fm

import kotlin.math.abs
import kotlin.random.Random

/**
 * Software FM tuner for HU without radio chip / bench testing.
 * Seek lands on [FmPresets] or random in-band freqs with fake RDS/signal.
 */
class SimFmTuner(
    private val region: FmRegion = FmRegion.ITU_2,
    private val presets: List<FmStation> = FmPresets.caracas,
) : FmTuner {
    override val name: String = "sim"

    private var powered = false
    private var freq = 95_500
    private var signal = 0
    private var rdsPs = ""
    private var rdsRt = ""
    private var seeking = false

    override fun open(): Boolean {
        powered = true
        applyMeta(freq)
        return true
    }

    override fun close() {
        powered = false
        signal = 0
        rdsPs = ""
        rdsRt = ""
        seeking = false
    }

    override fun setFrequency(khz: Int): Boolean {
        if (!powered) return false
        freq = FmFreq.snap(khz, region)
        applyMeta(freq)
        return true
    }

    override fun seek(up: Boolean): Int? {
        if (!powered) return null
        seeking = true
        val sorted = presets.map { it.freqKhz }.distinct().sorted()
        val next =
            if (up) {
                sorted.firstOrNull { it > freq } ?: sorted.firstOrNull()
            } else {
                sorted.lastOrNull { it < freq } ?: sorted.lastOrNull()
            }
        val landed = next ?: FmFreq.step(freq, region, up)
        freq = landed
        applyMeta(freq)
        seeking = false
        return freq
    }

    override fun current(): FmTunerState =
        FmTunerState(
            powered = powered,
            freqKhz = freq,
            stereo = signal >= 40,
            signalPct = signal,
            rdsPs = rdsPs,
            rdsRt = rdsRt,
            backend = name,
            seeking = seeking,
            status =
                when {
                    !powered -> "FM sim off"
                    seeking -> "Seek…"
                    rdsPs.isNotBlank() -> "FM sim · $rdsPs · ${FmFreq.formatMhz(freq)}"
                    else -> "FM sim · ${FmFreq.formatMhz(freq)} · sig $signal%"
                },
        )

    private fun applyMeta(khz: Int) {
        val preset = presets.minByOrNull { abs(it.freqKhz - khz) }
        if (preset != null && abs(preset.freqKhz - khz) <= region.stepKhz) {
            signal = 70 + Random.nextInt(25)
            rdsPs = preset.name.take(8)
            rdsRt = "${preset.city} · ${preset.genre}"
        } else {
            signal = 15 + Random.nextInt(35)
            rdsPs = ""
            rdsRt = "Sin RDS"
        }
    }
}
