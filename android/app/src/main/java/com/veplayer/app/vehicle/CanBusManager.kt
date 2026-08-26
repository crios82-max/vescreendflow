package com.veplayer.app.vehicle

import android.content.Context
import android.util.Log
import com.veplayer.app.data.VePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Owns the active [VehicleSignalAdapter] and mirrors into [VehicleState].
 */
object CanBusManager {
    private const val TAG = "CanBusManager"

    private var appContext: Context? = null
    private var appScope: CoroutineScope? = null
    private var prefs: VePrefs? = null
    private var adapter: VehicleSignalAdapter? = null
    private var gpsAdapter: GpsSpeedAdapter? = null
    private var collectJob: Job? = null

    @Synchronized
    fun start(context: Context) {
        if (appScope != null) {
            rebind()
            return
        }
        val app = context.applicationContext
        appContext = app
        prefs = VePrefs(app)
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        rebind()
    }

    @Synchronized
    fun rebind() {
        val p = prefs ?: return
        val scope = appScope ?: return
        val ctx = appContext ?: return
        collectJob?.cancel()
        adapter?.stop()
        gpsAdapter = null

        val kind = SignalSourceKind.fromId(p.signalSource)
        val next: VehicleSignalAdapter =
            when (kind) {
                SignalSourceKind.GPS -> GpsSpeedAdapter().also { gpsAdapter = it }
                SignalSourceKind.MOCK -> MockCanAdapter(p, scope)
                SignalSourceKind.CAN -> CanBusStubAdapter(p, scope)
                SignalSourceKind.OBD -> ObdElm327Adapter(ctx, p, scope)
            }
        adapter = next
        next.start()
        Log.i(TAG, "signal source → ${kind.id} (${next.name})")
        collectJob =
            scope.launch {
                next.signals.collectLatest { VehicleState.applySignals(it) }
            }
    }

    /** GPS path: SenseBridge feeds speed when source is GPS (or as fallback heading). */
    fun ingestGps(
        speedMps: Float?,
        headingDeg: Float? = null,
        reverseOverride: Boolean = false,
    ) {
        val p = prefs ?: return
        when (SignalSourceKind.fromId(p.signalSource)) {
            SignalSourceKind.GPS ->
                gpsAdapter?.ingest(speedMps, headingDeg, reverseOverride)
            else -> {
                // Still allow heading merge for surround without overriding CAN speed
                if (headingDeg != null) {
                    VehicleState.patchHeading(headingDeg)
                }
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        adapter?.stop()
        adapter = null
        gpsAdapter = null
        appScope?.cancel()
        appScope = null
        prefs = null
        appContext = null
    }

    fun bondedObdDevices(): List<ObdBondedDevice> {
        val ctx = appContext ?: return emptyList()
        return ObdBluetoothClient(ctx).bondedDevices()
    }
}
