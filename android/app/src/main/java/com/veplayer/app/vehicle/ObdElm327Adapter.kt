package com.veplayer.app.vehicle

import android.content.Context
import android.util.Log
import com.veplayer.app.data.VePrefs
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * OBD-II / ELM327 adapter.
 *
 * 1. If [VePrefs.obdDeviceAddress] is set → Bluetooth Classic RFCOMM + Mode 01 PIDs
 * 2. On failure / no MAC → PID simulator (`obd_sim`) so UI/fleet keep working
 * 3. DTC: live Modes 03/07/0A (+0101 MIL) every ~8s · sim seeds demo codes when enabled
 *
 * PIDs: 010D speed · 010C RPM · 0104 load · 0105 coolant · 010F intake · 015C oil · 012F fuel · 015E fuel rate · 0146 ambient · 0111 throttle · 011F runtime · 0142 voltage
 */
class ObdElm327Adapter(
    context: Context,
    private val prefs: VePrefs,
    private val scope: CoroutineScope,
) : VehicleSignalAdapter {
    private val app = context.applicationContext
    private val bt = ObdBluetoothClient(app)
    private val _signals = MutableStateFlow(VehicleSignals(source = "obd"))
    override val signals: StateFlow<VehicleSignals> = _signals.asStateFlow()
    override val name: String = "obd"

    private var job: Job? = null
    private var t = 0.0
    private var pollCycles = 0

    @Volatile
    var linkState: ObdLinkState = ObdLinkState.IDLE
        private set

    @Volatile
    var statusText: String = "idle"
        private set

    override fun start() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                val addr = prefs.obdDeviceAddress.trim()
                var live = false
                if (addr.isNotBlank()) {
                    linkState = ObdLinkState.CONNECTING
                    statusText = "Conectando $addr…"
                    ObdLinkBus.publish(linkState, statusText)
                    live = bt.connect(addr)
                    if (live) {
                        linkState = ObdLinkState.READY
                        statusText = "ELM327 OK · $addr"
                        ObdLinkBus.publish(linkState, statusText)
                        Log.i(TAG, statusText)
                    } else {
                        linkState = ObdLinkState.FALLBACK_SIM
                        statusText = "BT fail: ${bt.lastError} · sim"
                        ObdLinkBus.publish(linkState, statusText)
                        Log.w(TAG, statusText)
                    }
                } else {
                    linkState = ObdLinkState.FALLBACK_SIM
                    statusText = "Sin MAC · obd_sim"
                    ObdLinkBus.publish(linkState, statusText)
                }

                if (!live && prefs.dtcDemoSeed && DtcBus.snap.value.codes.isEmpty()) {
                    DtcBus.seedDemo()
                }

                while (isActive) {
                    t += 0.5
                    pollCycles++
                    if (live && bt.isConnected()) {
                        linkState = ObdLinkState.POLLING
                        val pids = bt.pollPids()
                        if (pids.speedKmh == null && pids.rpm == null) {
                            if (!bt.isConnected()) {
                                live = false
                                linkState = ObdLinkState.FALLBACK_SIM
                                statusText = "Link caído · sim"
                                ObdLinkBus.publish(linkState, statusText)
                                if (prefs.dtcDemoSeed) DtcBus.seedDemo()
                            } else {
                                _signals.value = fromPids(pids, live = true)
                            }
                        } else {
                            if (pollCycles % 16 == 1) {
                                runCatching { DtcBus.apply(bt.pollDtc()) }
                            }
                            _signals.value = fromPids(pids, live = true)
                            statusText =
                                "OBD live · ${pids.speedKmh?.toInt() ?: "—"} km/h · ${pids.rpm?.toInt() ?: "—"} rpm"
                            ObdLinkBus.publish(linkState, statusText)
                        }
                        delay(400)
                    } else {
                        _signals.value = simulatePids()
                        delay(500)
                    }
                }
            }
    }

    override fun stop() {
        job?.cancel()
        job = null
        bt.disconnect()
        linkState = ObdLinkState.IDLE
        statusText = "idle"
        ObdLinkBus.publish(linkState, statusText)
    }

    fun requestReadDtc() {
        scope.launch {
            if (bt.isConnected()) {
                runCatching { DtcBus.apply(bt.pollDtc()) }
            } else if (prefs.dtcDemoSeed) {
                DtcBus.seedDemo()
            }
        }
    }

    fun requestClearDtc(): Boolean {
        val liveOk = if (bt.isConnected()) bt.clearDtcs() else true
        DtcBus.clear()
        return liveOk
    }

    private fun withDtc(base: VehicleSignals): VehicleSignals {
        val d = DtcBus.snap.value
        return base.copy(
            mil = d.mil,
            dtcCount = if (d.dtcCount > 0) d.dtcCount else d.codes.size,
            dtcs = d.codes,
        )
    }

    private fun fromPids(
        p: ObdPidParser.PidValues,
        live: Boolean,
    ): VehicleSignals {
        val prev = _signals.value
        val kmh = p.speedKmh ?: (prev.speedKmh)
        val gear =
            when {
                prefs.mockReverse -> Gear.R
                kmh < 0.5f -> Gear.N
                else -> Gear.D
            }
        return withDtc(
            VehicleSignals(
                speedMps = kmh / 3.6f,
                gear = gear,
                turn = TurnSignal.OFF,
                parkingBrake = gear == Gear.N && kmh < 0.5f,
                seatbeltDriver = !prefs.seatbeltSim,
                fuelPct = p.fuelPct ?: prev.fuelPct,
                fuelRateGps = p.fuelRateGps ?: prev.fuelRateGps,
                rpm = p.rpm ?: prev.rpm,
                coolantC = p.coolantC ?: prev.coolantC,
                oilTempC = p.oilTempC ?: prev.oilTempC,
                intakeAirC = p.intakeAirC ?: prev.intakeAirC,
                batteryVoltageV = p.batteryVoltageV ?: prev.batteryVoltageV,
                outdoorTempC = p.outdoorTempC ?: prev.outdoorTempC,
                ignition = IgnitionState.ON,
                absActive = false,
                tpmsFlPsi = prev.tpmsFlPsi,
                tpmsFrPsi = prev.tpmsFrPsi,
                tpmsRlPsi = prev.tpmsRlPsi,
                tpmsRrPsi = prev.tpmsRrPsi,
                hvacCabinC = prev.hvacCabinC ?: p.outdoorTempC,
                hvacTargetC = prev.hvacTargetC,
                hvacAcOn = prev.hvacAcOn,
                hvacFanLevel = prev.hvacFanLevel,
                throttlePct = p.throttlePct ?: prev.throttlePct,
                engineLoadPct = p.engineLoadPct ?: prev.engineLoadPct,
                runtimeSec = p.runtimeSec ?: prev.runtimeSec,
                source = if (live) "obd" else "obd_sim",
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    private fun simulatePids(): VehicleSignals {
        val forcedKmh = prefs.mockSpeedKmh
        val forceReverse = prefs.mockReverse
        val kmh =
            if (forcedKmh > 0f || forceReverse) {
                forcedKmh
            } else {
                (28.0 + 12.0 * sin(t / 10.0)).toFloat()
            }
        val rpm = 900f + kmh * 35f
        val fuelGps = (kmh / 90f * 18f + 2f).coerceIn(0.5f, 40f)
        val gear =
            when {
                forceReverse -> Gear.R
                kmh < 0.5f -> Gear.N
                else -> Gear.D
            }
        val absPulse = ((t * 2).toInt() % 40) == 0 && kmh > 20f
        return withDtc(
            HvacClimateBus.applyToSignals(
                VehicleSignals(
                speedMps = kmh / 3.6f,
                gear = gear,
                turn = TurnSignal.OFF,
                parkingBrake = gear == Gear.N && kmh < 0.5f,
                seatbeltDriver = !prefs.seatbeltSim,
                batterySocPct = null,
                fuelPct = (55f + 3f * sin(t / 50.0).toFloat()).coerceIn(0f, 100f),
                fuelRateGps = fuelGps,
                rangeKm = null,
                rpm = rpm,
                steeringAngleDeg = (sin(t / 7.0) * 8.0).toFloat(),
                coolantC = 90f,
                oilTempC = 95f + (sin(t / 35.0).toFloat() * 3f),
                intakeAirC = 38f + (sin(t / 25.0).toFloat() * 4f),
                batteryVoltageV = 13.8f + (sin(t / 40.0).toFloat() * 0.15f),
                outdoorTempC = 27f,
                ignition = IgnitionState.ON,
                absActive = absPulse,
                tpmsFlPsi = 32.5f,
                tpmsFrPsi = 32.2f,
                tpmsRlPsi = 33.0f,
                tpmsRrPsi = 32.8f,
                hvacCabinC = 24f + sin(t / 30.0).toFloat(),
                hvacTargetC = 22f,
                hvacAcOn = true,
                hvacFanLevel = 2,
                throttlePct = (kmh / 90f * 100f).coerceIn(0f, 100f),
                engineLoadPct = (kmh / 90f * 85f).coerceIn(0f, 100f),
                runtimeSec = t.toInt().coerceAtLeast(0),
                source = "obd_sim",
                updatedAtMs = System.currentTimeMillis(),
                ),
            ),
        )
    }

    companion object {
        private const val TAG = "ObdElm327"
    }
}

/** UI-facing OBD link status. */
object ObdLinkBus {
    data class Snapshot(
        val state: ObdLinkState = ObdLinkState.IDLE,
        val text: String = "idle",
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun publish(
        link: ObdLinkState,
        text: String,
    ) {
        _state.value = Snapshot(link, text)
    }
}
