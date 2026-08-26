package com.veplayer.app.vehicle

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
 * Production path: Bluetooth Classic RFCOMM to an ELM327 dongle, ATZ / 010D / 010C / 0111 …
 * This build ships a **PID simulator** so UI + fleet work without hardware; when
 * [VePrefs.obdDeviceAddress] is set we log the target and keep simulated PIDs
 * (real BT stack can be wired without changing [CanBusManager]).
 *
 * Common PIDs mapped conceptually:
 * - 010D vehicle speed
 * - 010C RPM
 * - 0111 throttle
 * - 0105 coolant
 * - 012F fuel level
 */
class ObdElm327Adapter(
    private val prefs: VePrefs,
    private val scope: CoroutineScope,
) : VehicleSignalAdapter {
    private val _signals = MutableStateFlow(VehicleSignals(source = "obd"))
    override val signals: StateFlow<VehicleSignals> = _signals.asStateFlow()
    override val name: String = "obd"

    private var job: Job? = null
    private var t = 0.0

    override fun start() {
        if (job?.isActive == true) return
        val addr = prefs.obdDeviceAddress
        if (addr.isNotBlank()) {
            Log.i(TAG, "OBD target $addr — using PID simulator until RFCOMM is linked")
        } else {
            Log.i(TAG, "OBD adapter started without MAC — PID simulator")
        }
        job =
            scope.launch {
                while (isActive) {
                    t += 0.5
                    _signals.value = simulatePids()
                    delay(500)
                }
            }
    }

    override fun stop() {
        job?.cancel()
        job = null
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
        val gear =
            when {
                forceReverse -> Gear.R
                kmh < 0.5f -> Gear.N
                else -> Gear.D
            }
        return VehicleSignals(
            speedMps = kmh / 3.6f,
            gear = gear,
            turn = TurnSignal.OFF,
            parkingBrake = gear == Gear.N && kmh < 0.5f,
            seatbeltDriver = true,
            batterySocPct = null,
            fuelPct = (55f + 3f * sin(t / 50.0).toFloat()).coerceIn(0f, 100f),
            rangeKm = null,
            rpm = rpm,
            steeringAngleDeg = (sin(t / 7.0) * 8.0).toFloat(),
            coolantC = 90f,
            outdoorTempC = 27f,
            ignition = IgnitionState.ON,
            headingDeg = null,
            odometerKm = null,
            source = if (prefs.obdDeviceAddress.isNotBlank()) "obd" else "obd_sim",
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    companion object {
        private const val TAG = "ObdElm327"
    }
}
