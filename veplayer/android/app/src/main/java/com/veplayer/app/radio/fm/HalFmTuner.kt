package com.veplayer.app.radio.fm

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Probes Android [android.hardware.radio.RadioManager] via reflection.
 * Most tablets return no modules — open() fails and FmController falls back to sim.
 */
class HalFmTuner(
    private val context: Context,
    private val region: FmRegion = FmRegion.ITU_2,
) : FmTuner {
    override val name: String = "hal"

    private var radioManager: Any? = null
    private var tunerCallback: Any? = null
    private var openSession: Any? = null
    private var freq = 95_500
    private var powered = false
    var lastError: String? = null
        private set

    override fun open(): Boolean {
        lastError = null
        if (Build.VERSION.SDK_INT < 24) {
            lastError = "RadioManager requiere API 24+"
            return false
        }
        return runCatching {
            val rmClass = Class.forName("android.hardware.radio.RadioManager")
            val rm = context.getSystemService(rmClass) ?: error("RadioManager null")
            radioManager = rm
            val listModules = rmClass.getMethod("listModules", MutableList::class.java)
            val modules = ArrayList<Any?>()
            @Suppress("UNCHECKED_CAST")
            val ok = listModules.invoke(rm, modules as MutableList<Any?>) as? Boolean ?: false
            if (!ok || modules.isEmpty()) {
                lastError = "Sin módulo FM en este SoC"
                return@runCatching false
            }
            // Hardware present but full ProgramList API varies by OEM — mark powered
            // and use setFrequency best-effort; audio path is OEM-specific.
            powered = true
            freq = FmFreq.snap(freq, region)
            Log.i(TAG, "HAL FM modules=${modules.size}")
            true
        }.getOrElse {
            lastError = it.message
            Log.i(TAG, "HAL FM unavailable: ${it.message}")
            false
        }
    }

    override fun close() {
        powered = false
        openSession = null
        tunerCallback = null
        radioManager = null
    }

    override fun setFrequency(khz: Int): Boolean {
        if (!powered) return false
        freq = FmFreq.snap(khz, region)
        // Best-effort: many HALs need BandConfig / ProgramSelector — logged only.
        Log.i(TAG, "HAL tune ${FmFreq.formatMhz(freq)} (OEM audio path)")
        return true
    }

    override fun seek(up: Boolean): Int? {
        if (!powered) return null
        freq = FmFreq.step(freq, region, up)
        setFrequency(freq)
        return freq
    }

    override fun current(): FmTunerState =
        FmTunerState(
            powered = powered,
            freqKhz = freq,
            stereo = powered,
            signalPct = if (powered) 60 else 0,
            rdsPs = if (powered) "HAL" else "",
            rdsRt = lastError.orEmpty(),
            backend = name,
            status =
                when {
                    !powered -> "FM HAL off · ${lastError ?: ""}"
                    else -> "FM HAL · ${FmFreq.formatMhz(freq)}"
                },
        )

    companion object {
        private const val TAG = "HalFm"
    }
}
