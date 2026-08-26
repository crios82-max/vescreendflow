package com.veplayer.app.vehicle.can

import android.content.Context
import android.util.Log
import com.veplayer.app.data.VePrefs
import com.veplayer.app.vehicle.VehicleSignalAdapter
import com.veplayer.app.vehicle.VehicleSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class CanLinkState {
    IDLE,
    PROBING,
    LIVE,
    SIM,
    ERROR,
}

object CanLinkBus {
    data class Snapshot(
        val state: CanLinkState = CanLinkState.IDLE,
        val text: String = "idle",
        val backend: String = "",
        val usbDevices: List<String> = emptyList(),
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun publish(snap: Snapshot) {
        _state.value = snap
    }
}

/**
 * Real CAN path: pick transport (Car / USB SLCAN / SocketCAN / sim) and decode
 * frames with [CanSignalDecoder] into [VehicleSignals].
 */
class RealCanAdapter(
    private val context: Context,
    private val prefs: VePrefs,
    private val scope: CoroutineScope,
) : VehicleSignalAdapter {
    private val _signals = MutableStateFlow(VehicleSignals(source = "can"))
    override val signals: StateFlow<VehicleSignals> = _signals.asStateFlow()
    override val name: String = "can"

    private var job: Job? = null
    private var transport: CanTransport? = null

    override fun start() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                CanLinkBus.publish(
                    CanLinkBus.Snapshot(
                        state = CanLinkState.PROBING,
                        text = "Probando backend CAN…",
                        backend = prefs.canBackend,
                        usbDevices = UsbSlcanTransport(context).listCandidates(),
                    ),
                )
                val opened = openPreferred()
                if (opened == null) {
                    CanLinkBus.publish(
                        CanLinkBus.Snapshot(
                            state = CanLinkState.ERROR,
                            text = "No se pudo abrir CAN",
                            backend = prefs.canBackend,
                        ),
                    )
                    return@launch
                }
                transport = opened.first
                val tag = opened.second
                val isSim = opened.first is SimCanTransport
                CanSignalDecoder.ensureLoaded(context)
                CanLinkBus.publish(
                    CanLinkBus.Snapshot(
                        state = if (isSim) CanLinkState.SIM else CanLinkState.LIVE,
                        text = "CAN ${opened.first.name} · $tag · ${CanSignalDecoder.database()?.sourceLabel ?: "dbc?"}",
                        backend = opened.first.name,
                        usbDevices = UsbSlcanTransport(context).listCandidates(),
                    ),
                )
                Log.i(TAG, "using ${opened.first.name} ($tag)")

                var snap = VehicleSignals(source = tag, ignition = com.veplayer.app.vehicle.IgnitionState.ON)
                while (isActive) {
                    val frame = opened.first.readFrame(150)
                    if (frame != null) {
                        snap = CanSignalDecoder.apply(frame, snap, tag, context)
                        // Honour mock reverse override for bench
                        if (prefs.mockReverse) {
                            snap = snap.copy(gear = com.veplayer.app.vehicle.Gear.R)
                        }
                        if (prefs.mockSpeedKmh > 0f && isSim) {
                            snap =
                                snap.copy(
                                    speedMps = prefs.mockSpeedKmh / 3.6f,
                                    gear =
                                        if (prefs.mockReverse) {
                                            com.veplayer.app.vehicle.Gear.R
                                        } else if (prefs.mockSpeedKmh < 0.5f) {
                                            com.veplayer.app.vehicle.Gear.P
                                        } else {
                                            com.veplayer.app.vehicle.Gear.D
                                        },
                                )
                        }
                        _signals.value = snap
                    } else {
                        delay(40)
                    }
                }
            }
    }

    override fun stop() {
        job?.cancel()
        job = null
        transport?.close()
        transport = null
        CanLinkBus.publish(CanLinkBus.Snapshot(state = CanLinkState.IDLE, text = "idle"))
    }

    private fun openPreferred(): Pair<CanTransport, String>? {
        val backend = CanBackend.fromId(prefs.canBackend)
        val tries: List<() -> Pair<CanTransport, String>?> =
            when (backend) {
                CanBackend.CAR -> listOf({ tryOpen(CarPropertyTransport(context), "can_car") })
                CanBackend.USB -> listOf({ tryOpen(UsbSlcanTransport(context), "can_usb") })
                CanBackend.SOCKET -> listOf({ tryOpen(SocketCanTransport(prefs.canSocketIface), "can_socket") })
                CanBackend.SIM -> listOf({ tryOpen(SimCanTransport(), "can_sim") })
                CanBackend.AUTO ->
                    listOf(
                        { tryOpen(CarPropertyTransport(context), "can_car") },
                        { tryOpen(UsbSlcanTransport(context), "can_usb") },
                        { tryOpen(SocketCanTransport(prefs.canSocketIface), "can_socket") },
                        { tryOpen(SimCanTransport(), "can_sim") },
                    )
            }
        for (t in tries) {
            val r = t() ?: continue
            return r
        }
        return tryOpen(SimCanTransport(), "can_sim")
    }

    private fun tryOpen(
        transport: CanTransport,
        tag: String,
    ): Pair<CanTransport, String>? {
        val ok = runCatching { transport.open() }.getOrDefault(false)
        if (ok) return transport to tag
        val err =
            when (transport) {
                is UsbSlcanTransport -> transport.lastError
                is CarPropertyTransport -> transport.lastError
                is SocketCanTransport -> transport.lastError
                else -> null
            }
        Log.i(TAG, "skip ${transport.name}: ${err ?: "open=false"}")
        transport.close()
        return null
    }

    companion object {
        private const val TAG = "RealCan"
    }
}
