package com.veplayer.app.vehicle

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
import kotlin.random.Random

/**
 * Synthetic CAN stream for bench / UI demos.
 * Honours prefs mock speed & reverse when set; otherwise animates a drive cycle.
 */
class MockCanAdapter(
    private val prefs: VePrefs,
    private val scope: CoroutineScope,
    private val sourceTag: String = "mock",
) : VehicleSignalAdapter {
    private val _signals = MutableStateFlow(VehicleSignals(source = sourceTag))
    override val signals: StateFlow<VehicleSignals> = _signals.asStateFlow()
    override val name: String = sourceTag

    private var job: Job? = null
    private var t = 0.0

    override fun start() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                while (isActive) {
                    t += 0.25
                    _signals.value = tick()
                    delay(250)
                }
            }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }

    private fun tick(): VehicleSignals {
        val forcedKmh = prefs.mockSpeedKmh
        val forceReverse = prefs.mockReverse
        val kmh =
            if (forcedKmh > 0f || forceReverse) {
                forcedKmh.coerceAtLeast(0f)
            } else {
                // gentle sine cruise 18–52 km/h
                (35.0 + 17.0 * sin(t / 8.0)).toFloat().coerceIn(0f, 90f)
            }
        val gear =
            when {
                forceReverse -> Gear.R
                kmh < 0.5f -> Gear.P
                else -> Gear.D
            }
        val phase = ((t / 12.0) % 4).toInt()
        val turn =
            when {
                forceReverse -> TurnSignal.OFF
                phase == 1 -> TurnSignal.LEFT
                phase == 3 -> TurnSignal.RIGHT
                else -> TurnSignal.OFF
            }
        val steer =
            when (turn) {
                TurnSignal.LEFT -> -18f + Random.nextFloat() * 4f
                TurnSignal.RIGHT -> 18f + Random.nextFloat() * 4f
                else -> (sin(t / 5.0) * 6.0).toFloat()
            }
        val soc = (72f + 4f * sin(t / 40.0).toFloat()).coerceIn(5f, 100f)
        val absPulse = phase == 2 && kmh > 40f
        val base =
            VehicleSignals(
            speedMps = kmh / 3.6f,
            gear = gear,
            turn = turn,
            doorFl = prefs.doorAjarSim,
            doorFr = false,
            doorRl = false,
            doorRr = false,
            trunkOpen = false,
            hoodOpen = false,
            parkingBrake = gear == Gear.P,
            seatbeltDriver = !prefs.seatbeltSim,
            batterySocPct = soc,
            fuelPct = null,
            fuelRateGps = (kmh / 90f * 14f + 1.5f).coerceIn(0.5f, 35f),
            rangeKm = soc * 3.2f,
            rpm = if (gear == Gear.D) 1400f + kmh * 28f else 0f,
            steeringAngleDeg = steer,
            coolantC = 86f + Random.nextFloat(),
            oilTempC = 92f + Random.nextFloat() * 4f,
            intakeAirC = 35f + Random.nextFloat() * 5f,
            batteryVoltageV = 13.6f + Random.nextFloat() * 0.4f,
            outdoorTempC = 28f,
            ignition = IgnitionState.ON,
            headingDeg = ((t * 3.0) % 360.0).toFloat(),
            yawRateDegS = steer * 0.15f,
            odometerKm = 12450f + (t / 3600.0).toFloat(),
            absActive = absPulse,
            tpmsFlPsi = 32.4f + Random.nextFloat() * 0.4f,
            tpmsFrPsi = 32.1f + Random.nextFloat() * 0.4f,
            tpmsRlPsi = 33.0f,
            tpmsRrPsi = 32.7f,
            hvacCabinC = 23.5f + sin(t / 25.0).toFloat(),
            hvacTargetC = 22f,
            hvacAcOn = true,
            hvacFanLevel = if (kmh > 30f) 2 else 1,
            throttlePct = (kmh / 90f * 80f).coerceIn(0f, 100f),
            engineLoadPct = (kmh / 90f * 75f).coerceIn(0f, 100f),
            fuelTrimStftPct = (sin(t / 18.0).toFloat() * 6f).coerceIn(-25f, 25f),
            fuelTrimLtftPct = (sin(t / 22.0).toFloat() * 5f).coerceIn(-25f, 25f),
            mapKpa = (40f + kmh / 90f * 55f).coerceIn(25f, 100f),
            runtimeSec = t.toInt().coerceAtLeast(0),
            source = sourceTag,
            updatedAtMs = System.currentTimeMillis(),
        )
        return HvacClimateBus.applyToSignals(base)
    }
}
