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
            egtB1s5TempC = (369f + kmh / 90f * 229f).coerceIn(299f, 673f),
            egtB2s5TempC = (364f + kmh / 90f * 232f).coerceIn(294f, 676f),
            o2LambdaB1s3 = (0.92f + kmh / 90f * 0.16f).coerceIn(0.85f, 1.13f),
            o2LambdaB2s3 = (0.91f + kmh / 90f * 0.15f).coerceIn(0.84f, 1.12f),
            noxReagentQualHours = (3.5f + kmh / 90f * 7.5f).coerceIn(0f, 45f),
            noxWarningActive = 0,
            noxInduceLevel1 = 0,
            noxInduceLevel2 = 0,
            noxEgrValveCounterHours = (11f + kmh / 90f * 26f + sin(t / 68.5).toFloat() * 5.5f).coerceIn(0f, 190f),
            noxMonitorMalfunctionHours = (9f + kmh / 90f * 22f + sin(t / 69.5).toFloat() * 4.5f).coerceIn(0f, 170f),
            egtB1s6TempC = (367f + kmh / 90f * 227f).coerceIn(297f, 671f),
            egtB2s6TempC = (362f + kmh / 90f * 230f).coerceIn(292f, 674f),
            egtB1s7TempC = (365f + kmh / 90f * 225f).coerceIn(295f, 669f),
            egtB2s7TempC = (360f + kmh / 90f * 228f).coerceIn(290f, 672f),
            egtB1s8TempC = (363f + kmh / 90f * 223f).coerceIn(293f, 667f),
            egtB2s8TempC = (358f + kmh / 90f * 226f).coerceIn(288f, 670f),
            o2LambdaB1s4 = (0.93f + kmh / 90f * 0.15f).coerceIn(0.86f, 1.12f),
            o2LambdaB2s4 = (0.92f + kmh / 90f * 0.14f).coerceIn(0.85f, 1.11f),
            o2ConcB1s3Pct = (7.5f + kmh / 90f * 9.5f + sin(t / 18.5).toFloat() * 2.8f).coerceIn(2f, 27f),
            o2ConcB1s4Pct = (7.3f + kmh / 90f * 9.3f + sin(t / 18.7).toFloat() * 2.7f).coerceIn(2f, 26f),
            o2ConcB2s3Pct = (7.1f + kmh / 90f * 9.1f + sin(t / 18.9).toFloat() * 2.6f).coerceIn(2f, 25f),
            o2ConcB2s4Pct = (6.9f + kmh / 90f * 8.9f + sin(t / 19.1).toFloat() * 2.5f).coerceIn(2f, 24f),
            defDosingCmdPct = (26f + kmh / 90f * 40f + sin(t / 23.5).toFloat() * 7f).coerceIn(5f, 92f),
            noxCorrectedB1s1Ppm = (165f + kmh / 90f * 390f + sin(t / 19.8).toFloat() * 73f).coerceIn(50f, 1130f),
            noxCorrectedB1s2Ppm = (162f + kmh / 90f * 385f + sin(t / 20.0).toFloat() * 71f).coerceIn(50f, 1110f),
            noxCorrectedB2s1Ppm = (159f + kmh / 90f * 378f + sin(t / 20.2).toFloat() * 69f).coerceIn(50f, 1090f),
            noxCorrectedB2s2Ppm = (156f + kmh / 90f * 372f + sin(t / 20.4).toFloat() * 67f).coerceIn(50f, 1070f),
            noxConcS3Ppm = (152f + kmh / 90f * 365f + sin(t / 21.0).toFloat() * 65f).coerceIn(50f, 1050f),
            noxConcS4Ppm = (148f + kmh / 90f * 355f + sin(t / 21.3).toFloat() * 61f).coerceIn(50f, 1010f),
            noxCorrectedS3Ppm = (146f + kmh / 90f * 350f + sin(t / 21.5).toFloat() * 59f).coerceIn(50f, 990f),
            noxCorrectedS4Ppm = (144f + kmh / 90f * 345f + sin(t / 21.7).toFloat() * 57f).coerceIn(50f, 970f),
            cylinderFuelRateMg = (11f + kmh / 90f * 26f + sin(t / 15.5).toFloat() * 5.5f).coerceIn(2f, 75f),
            evapSysVaporPa = (sin(t / 17.2).toFloat() * 3000f).coerceIn(-8500f, 8500f),
            transGearRatio = (0.78f + kmh / 90f * 1.7f + sin(t / 13.5).toFloat() * 0.28f).coerceIn(0.5f, 4.2f),
            obdOdometerKm = 12450f + (t / 3600.0).toFloat(),
            absDisableSupported = 1,
            absDisabled = 0,
            fuelPressAKpa = (1700f + kmh / 90f * 2100f + sin(t / 11.8).toFloat() * 350f).coerceIn(200f, 4800f),
            fuelPressBKpa = (1680f + kmh / 90f * 2050f + sin(t / 12.2).toFloat() * 340f).coerceIn(200f, 4800f),
            reflashDistKm = (1100f + kmh / 90f * 2600f + sin(t / 54.0).toFloat() * 550f).coerceIn(0f, 18000f),
            fuelLevelInputAPct = (41f + sin(t / 37.5).toFloat() * 17f).coerceIn(8f, 90f),
            fuelLevelInputBPct = (39f + sin(t / 38.5).toFloat() * 15f).coerceIn(8f, 88f),
            epcsDiagTimeSec = (42f + kmh / 90f * 75f + sin(t / 47.0).toFloat() * 22f).coerceIn(0f, 230f),
            epcsDiagCount = (11f + kmh / 90f * 32f + sin(t / 51.0).toFloat() * 7f).coerceIn(0f, 190f),
            noxPcdLampOn = 0,
            particulateInduceStatus = 0,
            dpfRemovalCounter = (16f + kmh / 90f * 42f + sin(t / 55.5).toFloat() * 11f).coerceIn(0f, 380f),
            reagentInjectionFailCounter = (7f + kmh / 90f * 20f + sin(t / 56.5).toFloat() * 5f).coerceIn(0f, 190f),
            particulateMonitorMalfunctionCounter = (6f + kmh / 90f * 18f + sin(t / 57.5).toFloat() * 4f).coerceIn(0f, 170f),
            engineFuelRateGps = (1.1f + kmh / 90f * 2.6f + sin(t / 58.5).toFloat() * 0.5f).coerceIn(0f, 11f),
            engineExhaustFlowKgh = (16f + kmh / 90f * 30f + sin(t / 59.5).toFloat() * 7f).coerceIn(0f, 190f),
            fuelSysUsePct1 = (34f + kmh / 90f * 26f + sin(t / 60.5).toFloat() * 11f).coerceIn(0f, 96f),
            fuelSysUsePct2 = (27f + kmh / 90f * 22f + sin(t / 61.5).toFloat() * 9f).coerceIn(0f, 93f),
            fuelSysUsePct3 = (21f + kmh / 90f * 18f + sin(t / 62.5).toFloat() * 7f).coerceIn(0f, 88f),
            wwhObdContinuousMiHours = (7f + kmh / 90f * 16f + sin(t / 63.5).toFloat() * 3.5f).coerceIn(0f, 110f),
            wwhObdEcuB1Hours = (20f + kmh / 90f * 44f + sin(t / 64.5).toFloat() * 11f).coerceIn(0f, 380f),
            fuelSysCtlClosedCount = (3.5f + kmh / 90f * 2.8f + sin(t / 65.5).toFloat() * 1.2f).coerceIn(0f, 8f),
            wwhObdCumulativeMiHours = (18f + kmh / 90f * 42f + sin(t / 66.5).toFloat() * 10f).coerceIn(0f, 380f),
            hybridEvBattVoltageV = (315f + sin(t / 67.5).toFloat() * 22f).coerceIn(240f, 395f),
            hevBattCurrentA = (35f + sin(t / 70.0).toFloat() * 75f).coerceIn(-200f, 250f),
            hevModeCode = if ((t.toInt() / 40) % 5 == 0) 2f else if ((t.toInt() / 40) % 3 == 0) 1f else 0f,
            vSetKmh = 120f,
            engOdoKm = (84000f + t.toFloat() / 100f).coerceIn(0f, 500000f),
            hvBattSohPct = (87f + sin(t / 70.5).toFloat() * 5.5f).coerceIn(55f, 99f),
            hvessTempC = (31f + kmh / 90f * 7.5f + sin(t / 71.5).toFloat() * 3.8f).coerceIn(20f, 54f),
            hvessCurrentA = (sin(t / 72.5).toFloat() * 42f + kmh / 90f * 62f).coerceIn(-175f, 175f),
            hvessVoltageV = (365f + sin(t / 73.5).toFloat() * 17f).coerceIn(280f, 415f),
            hvCellMaxTempC = (33f + kmh / 90f * 5.8f + sin(t / 74.5).toFloat() * 2.8f).coerceIn(25f, 49f),
            hvBalHours = (16f + kmh / 90f * 38f + sin(t / 75.5).toFloat() * 9f).coerceIn(0f, 380f),
            hvCellMinVoltageV = (3.52f + sin(t / 76.5).toFloat() * 0.07f).coerceIn(3.15f, 3.85f),
            hvCellMaxVoltageV = (3.70f + sin(t / 77.5).toFloat() * 0.055f).coerceIn(3.35f, 4.05f),
            hvPwrAvailPct = (60f + sin(t / 78.5).toFloat() * 16f).coerceIn(10f, 92f),
            hvChgLimitA = (82f + sin(t / 79.5).toFloat() * 32f).coerceIn(5f, 175f),
            hvCellMinTempC = (27f + sin(t / 80.0).toFloat() * 3.8f).coerceIn(14f, 41f),
            hvDisLimitA = (88f + sin(t / 80.5).toFloat() * 36f).coerceIn(5f, 195f),
            hvEnrgInKwh = (4150f + kmh / 90f * 780f + sin(t / 81.0).toFloat() * 190f).coerceIn(0f, 50000f),
            hvEnrgOutKwh = (4050f + kmh / 90f * 720f + sin(t / 81.5).toFloat() * 170f).coerceIn(0f, 50000f),
            hvEnrgTputWh = (8.0e6f + kmh / 90f * 1.4e6f).coerceIn(0f, 1e8f),
            hvAcrKw = (10f + sin(t / 82.0).toFloat() * 25f).coerceIn(-40f, 80f),
            hvessSohPct = (87f + sin(t / 90.0).toFloat() * 5f).coerceIn(60f, 100f),
            hvMinSocPct = (11f + sin(t / 91.0).toFloat() * 4f).coerceIn(5f, 30f),
            hvMaxSocPct = (91f + sin(t / 92.0).toFloat() * 4f).coerceIn(70f, 100f),
            hvDcapKwh = (54f + sin(t / 93.0).toFloat() * 8f).coerceIn(20f, 90f),
            hvSocePct = (85f + sin(t / 94.0).toFloat() * 7f).coerceIn(55f, 100f),
            essCapKwh = (57f + sin(t / 95.0).toFloat() * 7f).coerceIn(20f, 95f),
            bcapReady = if ((t.toInt() / 40) % 8 == 0) 0 else 1,
            essRsrvRemKwh = (11f + sin(t / 96.0).toFloat() * 4f).coerceIn(1f, 30f),
            essRsrvInitKwh = (17f + sin(t / 97.0).toFloat() * 2f).coerceIn(5f, 40f),
            essHealthDistKm = (1100f + t.toFloat() / 10f).coerceIn(0f, 50000f),
            essChgLimKw = (44f + sin(t / 98.0).toFloat() * 12f).coerceIn(5f, 90f),
            essChgActKw = (16f + sin(t / 99.0).toFloat() * 24f).coerceIn(-40f, 80f),
            hvEnerRateWhs = (11f + sin(t / 100.0).toFloat() * 17f).coerceIn(-50f, 80f),
            hvCurrRateAhs = (3.5f + sin(t / 101.0).toFloat() * 5.5f).coerceIn(-20f, 25f),
            emRpmA = (6200f + kmh / 90f * 4300f + sin(t / 12.0).toFloat() * 750f).coerceIn(0f, 18000f),
            emTqANm = (75f + kmh / 90f * 115f + sin(t / 14.0).toFloat() * 38f).coerceIn(-200f, 350f),
            fcVoltV = (275f + sin(t / 102.0).toFloat() * 38f).coerceIn(180f, 380f),
            fcFuelRateGps = (0.75f + kmh / 90f * 1.4f + sin(t / 103.0).toFloat() * 0.35f).coerceIn(0f, 6f),
            fcCumulCurrMahs = (115f + sin(t / 104.0).toFloat() * 38f).coerceIn(20f, 300f),
            fcCumulEnerWhs = (7.5f + sin(t / 105.0).toFloat() * 4.5f).coerceIn(0f, 40f),
            psTrips = (400f + t.toFloat() / 80f).coerceIn(0f, 20000f),
            defFluidPct = (47f + sin(t / 41.0).toFloat() * 17f).coerceIn(11f, 87f),
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
