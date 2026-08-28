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
            catalystTempC = (450f + kmh / 90f * 200f).coerceIn(350f, 700f),
            mafGps = (10f + kmh / 90f * 60f).coerceIn(3f, 100f),
            fuelPressureKpa = (300f + kmh / 90f * 70f).coerceIn(270f, 420f),
            baroKpa = 91f + sin(t / 40.0).toFloat() * 2f,
            timingAdvanceDeg = (kmh / 90f * 22f + sin(t / 14.0).toFloat() * 5f).coerceIn(-5f, 40f),
            o2B1s1Volts = (0.3f + sin(t / 9.0).toFloat() * 0.3f).coerceIn(0.08f, 0.9f),
            absoluteLoadPct = (kmh / 90f * 82f).coerceIn(0f, 100f),
            relativeThrottlePct = (kmh / 90f * 72f).coerceIn(0f, 100f),
            accelPedalPct = (kmh / 90f * 78f).coerceIn(0f, 100f),
            o2B1s2Volts = (0.32f + sin(t / 10.0).toFloat() * 0.28f).coerceIn(0.08f, 0.9f),
            egrErrorPct = (sin(t / 22.0).toFloat() * 10f).coerceIn(-25f, 25f),
            equivRatio = (0.95f + sin(t / 12.0).toFloat() * 0.06f).coerceIn(0.8f, 1.2f),
            evapPurgePct = (30f + kmh / 90f * 35f).coerceIn(0f, 100f),
            ethanolPct = (10f + sin(t / 28.0).toFloat() * 4f).coerceIn(0f, 100f),
            evapVaporPa = (sin(t / 18.0).toFloat() * 2500f).coerceIn(-7000f, 7000f),
            fuelRailAbsKpa = (11000f + kmh / 90f * 3500f).coerceIn(7500f, 18000f),
            egrCmdPct = (20f + kmh / 90f * 40f).coerceIn(0f, 100f),
            relAccelPedalPct = (kmh / 90f * 70f).coerceIn(0f, 100f),
            driverTorquePct = (kmh / 90f * 45f + sin(t / 17.0).toFloat() * 12f).coerceIn(-70f, 70f),
            actualTorquePct = (kmh / 90f * 42f + sin(t / 15.0).toFloat() * 10f).coerceIn(-70f, 70f),
            catalystB2TempC = (420f + kmh / 90f * 280f).coerceIn(320f, 720f),
            catalystB1s2TempC = (430f + kmh / 90f * 270f).coerceIn(330f, 710f),
            catalystB2s2TempC = (415f + kmh / 90f * 275f).coerceIn(325f, 715f),
            catalystB1s3TempC = (425f + kmh / 90f * 265f).coerceIn(335f, 705f),
            catalystB2s3TempC = (410f + kmh / 90f * 278f).coerceIn(320f, 718f),
            catalystB1s4TempC = (420f + kmh / 90f * 260f).coerceIn(340f, 700f),
            catalystB2s4TempC = (415f + kmh / 90f * 265f).coerceIn(335f, 705f),
            fuelTrimStft2B1Pct = (sin(t / 20.0).toFloat() * 7f).coerceIn(-25f, 25f),
            fuelTrimLtft2B1Pct = (sin(t / 24.0).toFloat() * 6f).coerceIn(-25f, 25f),
            fuelTrimStft2B2Pct = (sin(t / 21.0).toFloat() * 7f).coerceIn(-25f, 25f),
            fuelTrimLtft2B2Pct = (sin(t / 25.0).toFloat() * 5f).coerceIn(-25f, 25f),
            catalystB1s5TempC = (418f + kmh / 90f * 255f).coerceIn(338f, 698f),
            catalystB2s5TempC = (412f + kmh / 90f * 258f).coerceIn(332f, 702f),
            fuelInjectTimingDeg = (sin(t / 14.0).toFloat() * 15f + kmh / 90f * 10f).coerceIn(-35f, 40f),
            hybridBattLifePct = (78f + sin(t / 32.0).toFloat() * 18f).coerceIn(25f, 100f),
            engineRefTorqueNm = (210f + kmh / 90f * 70f).coerceIn(140f, 350f),
            catalystB1s6TempC = (405f + kmh / 90f * 252f).coerceIn(330f, 695f),
            catalystB2s6TempC = (400f + kmh / 90f * 255f).coerceIn(325f, 698f),
            throttleBPct = (kmh / 90f * 68f).coerceIn(0f, 100f),
            throttleCPct = (kmh / 90f * 66f).coerceIn(0f, 100f),
            milTimeMin = if (DtcBus.snap.value.mil) (t / 60f).toInt().coerceAtLeast(0) else null,
            catalystB1s7TempC = (398f + kmh / 90f * 248f).coerceIn(328f, 692f),
            catalystB2s7TempC = (393f + kmh / 90f * 250f).coerceIn(323f, 694f),
            fuelTypeCode = 1,
            maxEquivRatio = 1.05f + sin(t / 18.0).toFloat() * 0.04f,
            maxMafGps = (175f + kmh / 90f * 75f).coerceIn(110f, 270f),
            catalystB1s8TempC = (381f + kmh / 90f * 242f).coerceIn(316f, 686f),
            catalystB2s8TempC = (376f + kmh / 90f * 245f).coerceIn(311f, 689f),
            maxAvailTorquePct = (55f + sin(t / 22.0).toFloat() * 8f).coerceIn(15f, 100f),
            mafSensorIatC = 42f + (sin(t / 26.0).toFloat() * 6f) + kmh / 90f * 12f,
            auxInputStatus = null,
            catalystB1s9TempC = (379f + kmh / 90f * 240f).coerceIn(314f, 684f),
            catalystB2s9TempC = (374f + kmh / 90f * 243f).coerceIn(309f, 687f),
            coolantEct2C = 88f + (sin(t / 24.0).toFloat() * 4f) + kmh / 90f * 8f,
            iatSensor2C = 40f + (sin(t / 28.0).toFloat() * 5f) + kmh / 90f * 10f,
            turboInletKpa = (120f + kmh / 90f * 85f).coerceIn(90f, 240f),
            catalystB1s10TempC = (377f + kmh / 90f * 238f).coerceIn(312f, 682f),
            catalystB2s10TempC = (372f + kmh / 90f * 241f).coerceIn(307f, 685f),
            egrTempC = 320f + (sin(t / 20.0).toFloat() * 40f) + kmh / 90f * 50f,
            dieselIafCmdPct = (40f + kmh / 90f * 45f).coerceIn(10f, 95f),
            thrActuatorPct = (kmh / 90f * 78f).coerceIn(0f, 100f),
            catalystB1s11TempC = (375f + kmh / 90f * 237f).coerceIn(311f, 681f),
            catalystB2s11TempC = (370f + kmh / 90f * 240f).coerceIn(306f, 684f),
            actualEgrPct = (38f + kmh / 90f * 48f).coerceIn(10f, 95f),
            injectCtrlKpa = (4500f + kmh / 90f * 5500f).coerceIn(2000f, 14000f),
            fuelCtrlKpa = (3800f + kmh / 90f * 4800f).coerceIn(1500f, 12000f),
            catalystB1s12TempC = (374f + kmh / 90f * 235f).coerceIn(309f, 679f),
            catalystB2s12TempC = (369f + kmh / 90f * 238f).coerceIn(304f, 682f),
            fuelTrimStftB2Pct = (sin(t / 17.0).toFloat() * 7f).coerceIn(-25f, 25f),
            fuelTrimLtftB2Pct = (sin(t / 18.0).toFloat() * 6f).coerceIn(-25f, 25f),
            catalystB1s13TempC = (373f + kmh / 90f * 233f).coerceIn(307f, 677f),
            catalystB2s13TempC = (368f + kmh / 90f * 236f).coerceIn(302f, 680f),
            catalystB1s14TempC = (371f + kmh / 90f * 231f).coerceIn(305f, 675f),
            catalystB2s14TempC = (366f + kmh / 90f * 234f).coerceIn(300f, 678f),
            o2LambdaB1 = (0.91f + kmh / 90f * 0.17f).coerceIn(0.84f, 1.14f),
            pmSensorB1Pct = (37f + kmh / 90f * 41f).coerceIn(5f, 91f),
            pmSensorB2Pct = (35f + kmh / 90f * 39f).coerceIn(5f, 89f),
            dpfTriggerPct = (42f + kmh / 90f * 40f).coerceIn(5f, 95f),
            throttleGPct = (kmh / 90f * 72f).coerceIn(0f, 100f),
            engineFrictionPct = (16f + kmh / 90f * 24f).coerceIn(-12f, 68f),
            runtimeSec = t.toInt().coerceAtLeast(0),
            source = sourceTag,
            updatedAtMs = System.currentTimeMillis(),
        )
        return HvacClimateBus.applyToSignals(base)
    }
}
