package com.veplayer.app.vehicle

import android.util.Log
import com.veplayer.app.data.VePrefs
import kotlinx.coroutines.CoroutineScope

/**
 * Placeholder for real CAN (SocketCAN / USB-CAN / head-unit SDK).
 * Until hardware is present, reuses [MockCanAdapter] tagged as `can_stub`
 * so the rest of the stack (UI, reverse→cam, fleet) exercises the CAN path.
 *
 * Swap [start] body for:
 * - JNI to SocketCAN (`can0`)
 * - USB serial SLCAN
 * - OEM Vehicle HAL / CarPropertyManager
 */
class CanBusStubAdapter(
    prefs: VePrefs,
    scope: CoroutineScope,
) : VehicleSignalAdapter {
    private val delegate = MockCanAdapter(prefs, scope, sourceTag = "can_stub")
    override val name: String = "can"
    override val signals = delegate.signals

    override fun start() {
        Log.i(TAG, "CAN stub active — replace with SocketCAN / USB-CAN / CarPropertyManager")
        delegate.start()
    }

    override fun stop() = delegate.stop()

    companion object {
        private const val TAG = "CanBusStub"
    }
}
