package com.veplayer.app.radio.fm

import android.content.Context
import android.util.Log
import com.veplayer.app.data.VePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class FmBackend(val id: String, val label: String) {
    AUTO("auto", "Auto"),
    HAL("hal", "HAL RadioManager"),
    SIM("sim", "Simulador"),
    ;

    companion object {
        fun fromId(id: String): FmBackend =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: AUTO
    }
}

/**
 * Owns the active [FmTuner] backend and publishes [FmTunerState] for UI / media hub.
 */
object FmController {
    private const val TAG = "FmController"

    private var app: Context? = null
    private var tuner: FmTuner? = null

    private val _state = MutableStateFlow(FmTunerState())
    val state: StateFlow<FmTunerState> = _state.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (app != null) return
        app = context.applicationContext
    }

    @Synchronized
    fun ensureOpen(prefs: VePrefs? = null): Boolean {
        val ctx = app ?: return false
        val p = prefs ?: VePrefs(ctx)
        if (tuner != null && _state.value.powered) return true
        closeQuiet()
        val region = FmRegion.fromId(p.fmRegion)
        val backend = FmBackend.fromId(p.fmBackend)
        val opened =
            when (backend) {
                FmBackend.HAL -> tryOpen(HalFmTuner(ctx, region))
                FmBackend.SIM -> tryOpen(SimFmTuner(region))
                FmBackend.AUTO ->
                    tryOpen(HalFmTuner(ctx, region))
                        ?: tryOpen(SimFmTuner(region))
            }
        tuner = opened
        if (opened == null) {
            _state.value = FmTunerState(status = "FM no disponible")
            return false
        }
        opened.setFrequency(p.fmLastFreqKhz)
        publish()
        Log.i(TAG, "FM open backend=${opened.name} freq=${p.fmLastFreqKhz}")
        return true
    }

    fun tune(
        khz: Int,
        prefs: VePrefs,
    ): Boolean {
        if (!ensureOpen(prefs)) return false
        val region = FmRegion.fromId(prefs.fmRegion)
        val snapped = FmFreq.snap(khz, region)
        val ok = tuner?.setFrequency(snapped) == true
        if (ok) {
            prefs.fmLastFreqKhz = snapped
            publish()
        }
        return ok
    }

    fun tunePreset(
        station: FmStation,
        prefs: VePrefs,
    ): Boolean = tune(station.freqKhz, prefs)

    fun seek(
        up: Boolean,
        prefs: VePrefs,
    ): Int? {
        if (!ensureOpen(prefs)) return null
        _state.update { it.copy(seeking = true, status = "Seek…") }
        val freq = tuner?.seek(up)
        if (freq != null) prefs.fmLastFreqKhz = freq
        publish()
        return freq
    }

    fun step(
        up: Boolean,
        prefs: VePrefs,
    ): Boolean {
        val region = FmRegion.fromId(prefs.fmRegion)
        val next = FmFreq.step(prefs.fmLastFreqKhz, region, up)
        return tune(next, prefs)
    }

    fun powerOff() {
        closeQuiet()
        _state.value = FmTunerState(status = "FM off")
    }

    fun publish() {
        val s = tuner?.current() ?: FmTunerState(status = "FM off")
        _state.value = s
    }

    private fun tryOpen(t: FmTuner): FmTuner? {
        val ok = runCatching { t.open() }.getOrDefault(false)
        if (ok) return t
        t.close()
        return null
    }

    private fun closeQuiet() {
        runCatching { tuner?.close() }
        tuner = null
    }
}
