package com.veplayer.app.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetClient
import com.veplayer.app.fleet.PanicBus
import com.veplayer.app.media.MediaSource
import com.veplayer.app.media.VeMediaHub
import com.veplayer.app.surround.ActorKind
import com.veplayer.app.surround.SurroundActor
import com.veplayer.app.surround.SurroundEngine
import com.veplayer.app.ui.theme.Card
import com.veplayer.app.ui.theme.Lane
import com.veplayer.app.ui.theme.Mist
import com.veplayer.app.ui.theme.Mute
import com.veplayer.app.ui.theme.Night
import com.veplayer.app.ui.theme.Road
import com.veplayer.app.brand.BrandBus
import com.veplayer.app.ui.brand.BrandLogo
import com.veplayer.app.vehicle.FuelRangeHud
import com.veplayer.app.vehicle.FuelRangeHudMonitor
import com.veplayer.app.vehicle.FuelRateMonitor
import com.veplayer.app.vehicle.MafAirflowMonitor
import com.veplayer.app.vehicle.FuelPressureMonitor
import com.veplayer.app.vehicle.BarometricPressureMonitor
import com.veplayer.app.vehicle.TimingAdvanceMonitor
import com.veplayer.app.vehicle.O2VoltageMonitor
import com.veplayer.app.vehicle.AbsoluteLoadMonitor
import com.veplayer.app.vehicle.RelativeThrottleMonitor
import com.veplayer.app.vehicle.AccelPedalMonitor
import com.veplayer.app.vehicle.O2B2VoltageMonitor
import com.veplayer.app.vehicle.EgrErrorMonitor
import com.veplayer.app.vehicle.EquivRatioMonitor
import com.veplayer.app.vehicle.EvapPurgeMonitor
import com.veplayer.app.vehicle.EthanolPctMonitor
import com.veplayer.app.vehicle.EvapVaporMonitor
import com.veplayer.app.vehicle.FuelRailAbsMonitor
import com.veplayer.app.vehicle.CommandedEgrMonitor
import com.veplayer.app.vehicle.RelAccelPedalMonitor
import com.veplayer.app.vehicle.DriverTorqueMonitor
import com.veplayer.app.vehicle.ActualTorqueMonitor
import com.veplayer.app.vehicle.CatalystB2Monitor
import com.veplayer.app.vehicle.CatalystB1S2Monitor
import com.veplayer.app.vehicle.CatalystB2S2Monitor
import com.veplayer.app.vehicle.CatalystB1S3Monitor
import com.veplayer.app.vehicle.CatalystB2S3Monitor
import com.veplayer.app.vehicle.CatalystB1S4Monitor
import com.veplayer.app.vehicle.CatalystB2S4Monitor
import com.veplayer.app.vehicle.FuelTrimStft2B1Monitor
import com.veplayer.app.vehicle.FuelTrimLtft2B1Monitor
import com.veplayer.app.vehicle.FuelTrimStft2B2Monitor
import com.veplayer.app.vehicle.FuelTrimLtft2B2Monitor
import com.veplayer.app.vehicle.CatalystB1S5Monitor
import com.veplayer.app.vehicle.CatalystB2S5Monitor
import com.veplayer.app.vehicle.FuelInjectTimingMonitor
import com.veplayer.app.vehicle.HybridBattLifeMonitor
import com.veplayer.app.vehicle.EngineRefTorqueMonitor
import com.veplayer.app.vehicle.CatalystB1S6Monitor
import com.veplayer.app.vehicle.CatalystB2S6Monitor
import com.veplayer.app.vehicle.ThrottleBMonitor
import com.veplayer.app.vehicle.ThrottleCMonitor
import com.veplayer.app.vehicle.MilTimeOnMonitor
import com.veplayer.app.vehicle.CatalystB1S7Monitor
import com.veplayer.app.vehicle.CatalystB2S7Monitor
import com.veplayer.app.vehicle.FuelTypeMonitor
import com.veplayer.app.vehicle.MaxEquivRatioMonitor
import com.veplayer.app.vehicle.MaxMafGpsMonitor
import com.veplayer.app.vehicle.CatalystB1S8Monitor
import com.veplayer.app.vehicle.CatalystB2S8Monitor
import com.veplayer.app.vehicle.MaxAvailTorqueMonitor
import com.veplayer.app.vehicle.MafSensorIatMonitor
import com.veplayer.app.vehicle.AuxInputStatusMonitor
import com.veplayer.app.vehicle.CatalystB1S9Monitor
import com.veplayer.app.vehicle.CatalystB2S9Monitor
import com.veplayer.app.vehicle.CoolantEct2Monitor
import com.veplayer.app.vehicle.IatSensor2Monitor
import com.veplayer.app.vehicle.TurboInletPressureMonitor
import com.veplayer.app.vehicle.CatalystB1S10Monitor
import com.veplayer.app.vehicle.CatalystB2S10Monitor
import com.veplayer.app.vehicle.EgrTemperatureMonitor
import com.veplayer.app.vehicle.DieselIntakeAirflowMonitor
import com.veplayer.app.vehicle.ThrottleActuatorMonitor
import com.veplayer.app.vehicle.CatalystB1S11Monitor
import com.veplayer.app.vehicle.CatalystB2S11Monitor
import com.veplayer.app.vehicle.ActualEgrMonitor
import com.veplayer.app.vehicle.InjectPressureControlMonitor
import com.veplayer.app.vehicle.FuelPressureControlMonitor
import com.veplayer.app.vehicle.CatalystB1S12Monitor
import com.veplayer.app.vehicle.CatalystB2S12Monitor
import com.veplayer.app.vehicle.FuelTrimStftB2Monitor
import com.veplayer.app.vehicle.FuelTrimLtftB2Monitor
import com.veplayer.app.vehicle.CatalystB1S13Monitor
import com.veplayer.app.vehicle.CatalystB2S13Monitor
import com.veplayer.app.vehicle.CatalystB1S14Monitor
import com.veplayer.app.vehicle.CatalystB2S14Monitor
import com.veplayer.app.vehicle.O2LambdaB1Monitor
import com.veplayer.app.vehicle.PmSensorB1Monitor
import com.veplayer.app.vehicle.PmSensorB2Monitor
import com.veplayer.app.vehicle.EgtB1S5Monitor
import com.veplayer.app.vehicle.EgtB2S5Monitor
import com.veplayer.app.vehicle.O2LambdaB1S3Monitor
import com.veplayer.app.vehicle.O2LambdaB2S3Monitor
import com.veplayer.app.vehicle.NoxReagentQualityMonitor
import com.veplayer.app.vehicle.EgtB1S6Monitor
import com.veplayer.app.vehicle.EgtB2S6Monitor
import com.veplayer.app.vehicle.O2LambdaB1S4Monitor
import com.veplayer.app.vehicle.O2LambdaB2S4Monitor
import com.veplayer.app.vehicle.DefFluidMonitor
import com.veplayer.app.vehicle.EgtB1S7Monitor
import com.veplayer.app.vehicle.EgtB2S7Monitor
import com.veplayer.app.vehicle.EgtB1S8Monitor
import com.veplayer.app.vehicle.EgtB2S8Monitor
import com.veplayer.app.vehicle.O2ConcB1S3Monitor
import com.veplayer.app.vehicle.O2ConcB1S4Monitor
import com.veplayer.app.vehicle.O2ConcB2S3Monitor
import com.veplayer.app.vehicle.O2ConcB2S4Monitor
import com.veplayer.app.vehicle.DefDosingCmdMonitor
import com.veplayer.app.vehicle.NoxCorrectedB1S1Monitor
import com.veplayer.app.vehicle.NoxCorrectedB1S2Monitor
import com.veplayer.app.vehicle.NoxCorrectedB2S1Monitor
import com.veplayer.app.vehicle.NoxCorrectedB2S2Monitor
import com.veplayer.app.vehicle.NoxConcS3Monitor
import com.veplayer.app.vehicle.NoxConcS4Monitor
import com.veplayer.app.vehicle.NoxCorrectedS3Monitor
import com.veplayer.app.vehicle.NoxCorrectedS4Monitor
import com.veplayer.app.vehicle.CylinderFuelRateMonitor
import com.veplayer.app.vehicle.EvapSysVaporMonitor
import com.veplayer.app.vehicle.TransGearRatioMonitor
import com.veplayer.app.vehicle.ObdOdometerMonitor
import com.veplayer.app.vehicle.AbsDisableMonitor
import com.veplayer.app.vehicle.FuelPressAMonitor
import com.veplayer.app.vehicle.FuelPressBMonitor
import com.veplayer.app.vehicle.ReflashDistanceMonitor
import com.veplayer.app.vehicle.FuelLevelInputAMonitor
import com.veplayer.app.vehicle.FuelLevelInputBMonitor
import com.veplayer.app.vehicle.EpcsDiagTimeMonitor
import com.veplayer.app.vehicle.EpcsDiagCountMonitor
import com.veplayer.app.vehicle.NoxPcdLampMonitor
import com.veplayer.app.vehicle.ParticulateInduceWarnMonitor
import com.veplayer.app.vehicle.ParticulateInduceAlertMonitor
import com.veplayer.app.vehicle.DpfRemovalCounterMonitor
import com.veplayer.app.vehicle.ReagentInjectionFailCounterMonitor
import com.veplayer.app.vehicle.ParticulateMonitorMalfunctionCounterMonitor
import com.veplayer.app.vehicle.EngineFuelRateGpsMonitor
import com.veplayer.app.vehicle.EngineExhaustFlowMonitor
import com.veplayer.app.vehicle.FuelSysUsePct1Monitor
import com.veplayer.app.vehicle.FuelSysUsePct2Monitor
import com.veplayer.app.vehicle.FuelSysUsePct3Monitor
import com.veplayer.app.vehicle.WwhObdContinuousMiMonitor
import com.veplayer.app.vehicle.WwhObdEcuB1HoursMonitor
import com.veplayer.app.vehicle.WwhObdCumulativeMiMonitor
import com.veplayer.app.vehicle.FuelSysCtlClosedMonitor
import com.veplayer.app.vehicle.HybridEvBattVoltageMonitor
import com.veplayer.app.vehicle.NoxWarnActiveMonitor
import com.veplayer.app.vehicle.NoxInduceLevel1Monitor
import com.veplayer.app.vehicle.NoxInduceLevel2Monitor
import com.veplayer.app.vehicle.NoxEgrCounterMonitor
import com.veplayer.app.vehicle.NoxMonitorMalfunctionMonitor
import com.veplayer.app.vehicle.HvBattSoh
import com.veplayer.app.vehicle.HvBattSohMonitor
import com.veplayer.app.vehicle.HvessTemp
import com.veplayer.app.vehicle.HvessTempMonitor
import com.veplayer.app.vehicle.HvessCurrent
import com.veplayer.app.vehicle.HvessCurrentMonitor
import com.veplayer.app.vehicle.HvessPackVoltage
import com.veplayer.app.vehicle.HvessPackVoltageMonitor
import com.veplayer.app.vehicle.HvCellMaxTemp
import com.veplayer.app.vehicle.HvCellMaxTempMonitor
import com.veplayer.app.vehicle.HvBalHours
import com.veplayer.app.vehicle.HvBalHoursMonitor
import com.veplayer.app.vehicle.HvCellMinVolt
import com.veplayer.app.vehicle.HvCellMinVoltMonitor
import com.veplayer.app.vehicle.HvCellMaxVolt
import com.veplayer.app.vehicle.HvCellMaxVoltMonitor
import com.veplayer.app.vehicle.HvPwrAvail
import com.veplayer.app.vehicle.HvPwrAvailMonitor
import com.veplayer.app.vehicle.HvChgLimit
import com.veplayer.app.vehicle.HvChgLimitMonitor
import com.veplayer.app.vehicle.HvCellMinTemp
import com.veplayer.app.vehicle.HvCellMinTempMonitor
import com.veplayer.app.vehicle.HvDisLimit
import com.veplayer.app.vehicle.HvDisLimitMonitor
import com.veplayer.app.vehicle.HvEnrgIn
import com.veplayer.app.vehicle.HvEnrgInMonitor
import com.veplayer.app.vehicle.HvEnrgOut
import com.veplayer.app.vehicle.HvEnrgOutMonitor
import com.veplayer.app.vehicle.HvEnrgTput
import com.veplayer.app.vehicle.HvEnrgTputMonitor
import com.veplayer.app.vehicle.HvAcr
import com.veplayer.app.vehicle.HvAcrMonitor
import com.veplayer.app.vehicle.HvessSoh
import com.veplayer.app.vehicle.HvessSohMonitor
import com.veplayer.app.vehicle.HvMinSoc
import com.veplayer.app.vehicle.HvMinSocMonitor
import com.veplayer.app.vehicle.HvMaxSoc
import com.veplayer.app.vehicle.HvMaxSocMonitor
import com.veplayer.app.vehicle.HvDcap
import com.veplayer.app.vehicle.HvDcapMonitor
import com.veplayer.app.vehicle.HvSoce
import com.veplayer.app.vehicle.HvSoceMonitor
import com.veplayer.app.vehicle.EssCap
import com.veplayer.app.vehicle.EssCapMonitor
import com.veplayer.app.vehicle.BcapReady
import com.veplayer.app.vehicle.BcapReadyMonitor
import com.veplayer.app.vehicle.EssRsrv
import com.veplayer.app.vehicle.EssRsrvMonitor
import com.veplayer.app.vehicle.EssChgLim
import com.veplayer.app.vehicle.EssChgLimMonitor
import com.veplayer.app.vehicle.EssChgAct
import com.veplayer.app.vehicle.EssChgActMonitor
import com.veplayer.app.vehicle.HvEnerRate
import com.veplayer.app.vehicle.HvEnerRateMonitor
import com.veplayer.app.vehicle.HvCurrRate
import com.veplayer.app.vehicle.HvCurrRateMonitor
import com.veplayer.app.vehicle.EmRpm
import com.veplayer.app.vehicle.EmRpmMonitor
import com.veplayer.app.vehicle.EmTq
import com.veplayer.app.vehicle.EmTqMonitor
import com.veplayer.app.vehicle.FcVolt
import com.veplayer.app.vehicle.FcVoltMonitor
import com.veplayer.app.vehicle.FcFuelRate
import com.veplayer.app.vehicle.FcFuelRateMonitor
import com.veplayer.app.vehicle.PsTrips
import com.veplayer.app.vehicle.PsTripsMonitor
import com.veplayer.app.vehicle.HevMode
import com.veplayer.app.vehicle.HevModeMonitor
import com.veplayer.app.vehicle.HevBattCurr
import com.veplayer.app.vehicle.HevBattCurrMonitor
import com.veplayer.app.vehicle.VSet
import com.veplayer.app.vehicle.VSetMonitor
import com.veplayer.app.vehicle.DpfAftertreatmentMonitor
import com.veplayer.app.vehicle.ThrottleGMonitor
import com.veplayer.app.vehicle.EngineFrictionTorqueMonitor
import com.veplayer.app.vehicle.Gear
import com.veplayer.app.vehicle.GearRollMonitor
import com.veplayer.app.vehicle.IdleAlert
import com.veplayer.app.vehicle.IdleMonitor
import com.veplayer.app.vehicle.DtcMonitor
import com.veplayer.app.vehicle.DistSinceClearMonitor
import com.veplayer.app.vehicle.CabinOvertempMonitor
import com.veplayer.app.vehicle.CoolantOverheatMonitor
import com.veplayer.app.vehicle.DoorAjarMonitor
import com.veplayer.app.vehicle.DriverScoreMonitor
import com.veplayer.app.vehicle.EcoLiveMonitor
import com.veplayer.app.vehicle.EngineLoadMonitor
import com.veplayer.app.vehicle.FuelTrimStftMonitor
import com.veplayer.app.vehicle.FuelTrimLtftMonitor
import com.veplayer.app.vehicle.MapPressureMonitor
import com.veplayer.app.vehicle.EngineRuntimeMonitor
import com.veplayer.app.vehicle.HarshDrivingMonitor
import com.veplayer.app.vehicle.HazardStuckMonitor
import com.veplayer.app.vehicle.HighThrottleMonitor
import com.veplayer.app.vehicle.ImpactDetectMonitor
import com.veplayer.app.vehicle.HvacClimateMonitor
import com.veplayer.app.vehicle.IceFrostMonitor
import com.veplayer.app.vehicle.IntakeAirMonitor
import com.veplayer.app.vehicle.OilTempMonitor
import com.veplayer.app.vehicle.CatalystTempMonitor
import com.veplayer.app.vehicle.ParkingBrakeMovingMonitor
import com.veplayer.app.vehicle.ParkingDistanceMonitor
import com.veplayer.app.vehicle.RestBreakMonitor
import com.veplayer.app.vehicle.RouteDeviationMonitor
import com.veplayer.app.vehicle.RpmOverRevMonitor
import com.veplayer.app.vehicle.SeatbeltMonitor
import com.veplayer.app.vehicle.ShiftFatigueMonitor
import com.veplayer.app.vehicle.AbsHudMonitor
import com.veplayer.app.vehicle.BatteryVoltageMonitor
import com.veplayer.app.vehicle.SuddenFuelDropMonitor
import com.veplayer.app.vehicle.TpmsHudMonitor
import com.veplayer.app.vehicle.TurnStuckMonitor
import com.veplayer.app.vehicle.UnauthorizedMoveMonitor
import com.veplayer.app.vehicle.MaintenanceMonitor
import com.veplayer.app.vehicle.MilDistanceMonitor
import com.veplayer.app.vehicle.SpeedHud
import com.veplayer.app.vehicle.SpeedHudMonitor
import com.veplayer.app.vehicle.TurnSignal
import com.veplayer.app.vehicle.VehicleSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DriveVizPanel(
    vehicle: VehicleSnapshot,
    modifier: Modifier = Modifier,
) {
    val surround by SurroundEngine.snapshot.collectAsState()
    val media by VeMediaHub.nowPlaying.collectAsState()
    val context = LocalContext.current
    val prefs = remember { VePrefs(context) }
    val fleet = remember { FleetClient(prefs) }
    val scope = rememberCoroutineScope()
    val hud by SpeedHudMonitor.state.collectAsState()
    val maint by MaintenanceMonitor.state.collectAsState()
    val fuelHud by FuelRangeHudMonitor.state.collectAsState()
    val fuelRate by FuelRateMonitor.state.collectAsState()
    val mafFlow by MafAirflowMonitor.state.collectAsState()
    val fuelPress by FuelPressureMonitor.state.collectAsState()
    val baroPress by BarometricPressureMonitor.state.collectAsState()
    val timingAdv by TimingAdvanceMonitor.state.collectAsState()
    val o2Volt by O2VoltageMonitor.state.collectAsState()
    val absLoad by AbsoluteLoadMonitor.state.collectAsState()
    val relThr by RelativeThrottleMonitor.state.collectAsState()
    val accelPedal by AccelPedalMonitor.state.collectAsState()
    val o2B2 by O2B2VoltageMonitor.state.collectAsState()
    val egrErr by EgrErrorMonitor.state.collectAsState()
    val equiv by EquivRatioMonitor.state.collectAsState()
    val evapPur by EvapPurgeMonitor.state.collectAsState()
    val ethanol by EthanolPctMonitor.state.collectAsState()
    val evapVap by EvapVaporMonitor.state.collectAsState()
    val railAbs by FuelRailAbsMonitor.state.collectAsState()
    val egrCmd by CommandedEgrMonitor.state.collectAsState()
    val relAped by RelAccelPedalMonitor.state.collectAsState()
    val drvTorque by DriverTorqueMonitor.state.collectAsState()
    val actTorque by ActualTorqueMonitor.state.collectAsState()
    val catB2 by CatalystB2Monitor.state.collectAsState()
    val catB1s2 by CatalystB1S2Monitor.state.collectAsState()
    val catB2s2 by CatalystB2S2Monitor.state.collectAsState()
    val catB1s3 by CatalystB1S3Monitor.state.collectAsState()
    val catB2s3 by CatalystB2S3Monitor.state.collectAsState()
    val catB1s4 by CatalystB1S4Monitor.state.collectAsState()
    val catB2s4 by CatalystB2S4Monitor.state.collectAsState()
    val stft2B1 by FuelTrimStft2B1Monitor.state.collectAsState()
    val ltft2B1 by FuelTrimLtft2B1Monitor.state.collectAsState()
    val stft2B2 by FuelTrimStft2B2Monitor.state.collectAsState()
    val ltft2B2 by FuelTrimLtft2B2Monitor.state.collectAsState()
    val catB1s5 by CatalystB1S5Monitor.state.collectAsState()
    val catB2s5 by CatalystB2S5Monitor.state.collectAsState()
    val inject by FuelInjectTimingMonitor.state.collectAsState()
    val hybridBatt by HybridBattLifeMonitor.state.collectAsState()
    val refTorque by EngineRefTorqueMonitor.state.collectAsState()
    val catB1s6 by CatalystB1S6Monitor.state.collectAsState()
    val catB2s6 by CatalystB2S6Monitor.state.collectAsState()
    val thrB by ThrottleBMonitor.state.collectAsState()
    val thrC by ThrottleCMonitor.state.collectAsState()
    val milTime by MilTimeOnMonitor.state.collectAsState()
    val catB1s7 by CatalystB1S7Monitor.state.collectAsState()
    val catB2s7 by CatalystB2S7Monitor.state.collectAsState()
    val fuelType by FuelTypeMonitor.state.collectAsState()
    val maxEquiv by MaxEquivRatioMonitor.state.collectAsState()
    val maxMaf by MaxMafGpsMonitor.state.collectAsState()
    val catB1s8 by CatalystB1S8Monitor.state.collectAsState()
    val catB2s8 by CatalystB2S8Monitor.state.collectAsState()
    val maxAvailTorque by MaxAvailTorqueMonitor.state.collectAsState()
    val mafIat by MafSensorIatMonitor.state.collectAsState()
    val auxInput by AuxInputStatusMonitor.state.collectAsState()
    val catB1s9 by CatalystB1S9Monitor.state.collectAsState()
    val catB2s9 by CatalystB2S9Monitor.state.collectAsState()
    val ect2 by CoolantEct2Monitor.state.collectAsState()
    val iat2 by IatSensor2Monitor.state.collectAsState()
    val turboInlet by TurboInletPressureMonitor.state.collectAsState()
    val catB1s10 by CatalystB1S10Monitor.state.collectAsState()
    val catB2s10 by CatalystB2S10Monitor.state.collectAsState()
    val egrTemp by EgrTemperatureMonitor.state.collectAsState()
    val dieselIaf by DieselIntakeAirflowMonitor.state.collectAsState()
    val thrAct by ThrottleActuatorMonitor.state.collectAsState()
    val catB1s11 by CatalystB1S11Monitor.state.collectAsState()
    val catB2s11 by CatalystB2S11Monitor.state.collectAsState()
    val egrActual by ActualEgrMonitor.state.collectAsState()
    val injectCtrl by InjectPressureControlMonitor.state.collectAsState()
    val fuelCtrl by FuelPressureControlMonitor.state.collectAsState()
    val catB1s12 by CatalystB1S12Monitor.state.collectAsState()
    val catB2s12 by CatalystB2S12Monitor.state.collectAsState()
    val stftB2 by FuelTrimStftB2Monitor.state.collectAsState()
    val ltftB2 by FuelTrimLtftB2Monitor.state.collectAsState()
    val catB1s13 by CatalystB1S13Monitor.state.collectAsState()
    val catB2s13 by CatalystB2S13Monitor.state.collectAsState()
    val dpfTrig by DpfAftertreatmentMonitor.state.collectAsState()
    val thrG by ThrottleGMonitor.state.collectAsState()
    val engFriction by EngineFrictionTorqueMonitor.state.collectAsState()
    val catB1s14 by CatalystB1S14Monitor.state.collectAsState()
    val catB2s14 by CatalystB2S14Monitor.state.collectAsState()
    val o2Lambda by O2LambdaB1Monitor.state.collectAsState()
    val pmB1 by PmSensorB1Monitor.state.collectAsState()
    val pmB2 by PmSensorB2Monitor.state.collectAsState()
    val egtB1s5 by EgtB1S5Monitor.state.collectAsState()
    val egtB2s5 by EgtB2S5Monitor.state.collectAsState()
    val o2LambdaB1s3 by O2LambdaB1S3Monitor.state.collectAsState()
    val o2LambdaB2s3 by O2LambdaB2S3Monitor.state.collectAsState()
    val noxReq by NoxReagentQualityMonitor.state.collectAsState()
    val egtB1s6 by EgtB1S6Monitor.state.collectAsState()
    val egtB2s6 by EgtB2S6Monitor.state.collectAsState()
    val o2LambdaB1s4 by O2LambdaB1S4Monitor.state.collectAsState()
    val o2LambdaB2s4 by O2LambdaB2S4Monitor.state.collectAsState()
    val defFluid by DefFluidMonitor.state.collectAsState()
    val egtB1s7 by EgtB1S7Monitor.state.collectAsState()
    val egtB2s7 by EgtB2S7Monitor.state.collectAsState()
    val egtB1s8 by EgtB1S8Monitor.state.collectAsState()
    val egtB2s8 by EgtB2S8Monitor.state.collectAsState()
    val o2ConcB1s3 by O2ConcB1S3Monitor.state.collectAsState()
    val o2ConcB1s4 by O2ConcB1S4Monitor.state.collectAsState()
    val o2ConcB2s3 by O2ConcB2S3Monitor.state.collectAsState()
    val o2ConcB2s4 by O2ConcB2S4Monitor.state.collectAsState()
    val defDose by DefDosingCmdMonitor.state.collectAsState()
    val noxCorrB1s1 by NoxCorrectedB1S1Monitor.state.collectAsState()
    val noxCorrB1s2 by NoxCorrectedB1S2Monitor.state.collectAsState()
    val noxCorrB2s1 by NoxCorrectedB2S1Monitor.state.collectAsState()
    val noxCorrB2s2 by NoxCorrectedB2S2Monitor.state.collectAsState()
    val noxConcS3 by NoxConcS3Monitor.state.collectAsState()
    val noxConcS4 by NoxConcS4Monitor.state.collectAsState()
    val noxCorrS3 by NoxCorrectedS3Monitor.state.collectAsState()
    val noxCorrS4 by NoxCorrectedS4Monitor.state.collectAsState()
    val cylFuel by CylinderFuelRateMonitor.state.collectAsState()
    val evapSysVapor by EvapSysVaporMonitor.state.collectAsState()
    val transGear by TransGearRatioMonitor.state.collectAsState()
    val obdOdo by ObdOdometerMonitor.state.collectAsState()
    val absDisable by AbsDisableMonitor.state.collectAsState()
    val fuelPressA by FuelPressAMonitor.state.collectAsState()
    val fuelPressB by FuelPressBMonitor.state.collectAsState()
    val reflashDist by ReflashDistanceMonitor.state.collectAsState()
    val fuelLvlA by FuelLevelInputAMonitor.state.collectAsState()
    val fuelLvlB by FuelLevelInputBMonitor.state.collectAsState()
    val epcsTime by EpcsDiagTimeMonitor.state.collectAsState()
    val epcsCount by EpcsDiagCountMonitor.state.collectAsState()
    val noxPcdLamp by NoxPcdLampMonitor.state.collectAsState()
    val particulateInduceWarn by ParticulateInduceWarnMonitor.state.collectAsState()
    val particulateInduceAlert by ParticulateInduceAlertMonitor.state.collectAsState()
    val dpfRemoval by DpfRemovalCounterMonitor.state.collectAsState()
    val reagentFail by ReagentInjectionFailCounterMonitor.state.collectAsState()
    val particulateMalf by ParticulateMonitorMalfunctionCounterMonitor.state.collectAsState()
    val engineFuelRateGps by EngineFuelRateGpsMonitor.state.collectAsState()
    val exhaustFlow by EngineExhaustFlowMonitor.state.collectAsState()
    val fuelSysUse1 by FuelSysUsePct1Monitor.state.collectAsState()
    val fuelSysUse2 by FuelSysUsePct2Monitor.state.collectAsState()
    val fuelSysUse3 by FuelSysUsePct3Monitor.state.collectAsState()
    val wwhContMi by WwhObdContinuousMiMonitor.state.collectAsState()
    val wwhEcuB1 by WwhObdEcuB1HoursMonitor.state.collectAsState()
    val wwhCumMi by WwhObdCumulativeMiMonitor.state.collectAsState()
    val fuelSysCtl by FuelSysCtlClosedMonitor.state.collectAsState()
    val hevVolt by HybridEvBattVoltageMonitor.state.collectAsState()
    val noxWarn by NoxWarnActiveMonitor.state.collectAsState()
    val noxIndL1 by NoxInduceLevel1Monitor.state.collectAsState()
    val noxIndL2 by NoxInduceLevel2Monitor.state.collectAsState()
    val noxEgr by NoxEgrCounterMonitor.state.collectAsState()
    val noxMal by NoxMonitorMalfunctionMonitor.state.collectAsState()
    val hvSoh by HvBattSohMonitor.state.collectAsState()
    val hvessTemp by HvessTempMonitor.state.collectAsState()
    val hvessCur by HvessCurrentMonitor.state.collectAsState()
    val hvessVolt by HvessPackVoltageMonitor.state.collectAsState()
    val hvCellMax by HvCellMaxTempMonitor.state.collectAsState()
    val hvBal by HvBalHoursMonitor.state.collectAsState()
    val hvCellMinV by HvCellMinVoltMonitor.state.collectAsState()
    val hvCellMaxV by HvCellMaxVoltMonitor.state.collectAsState()
    val hvPwr by HvPwrAvailMonitor.state.collectAsState()
    val hvChg by HvChgLimitMonitor.state.collectAsState()
    val hvCellMinT by HvCellMinTempMonitor.state.collectAsState()
    val hvDis by HvDisLimitMonitor.state.collectAsState()
    val hvEnrgIn by HvEnrgInMonitor.state.collectAsState()
    val hvEnrgOut by HvEnrgOutMonitor.state.collectAsState()
    val hvEnrgTput by HvEnrgTputMonitor.state.collectAsState()
    val hvAcr by HvAcrMonitor.state.collectAsState()
    val hvessSoh by HvessSohMonitor.state.collectAsState()
    val hvMinSoc by HvMinSocMonitor.state.collectAsState()
    val hvMaxSoc by HvMaxSocMonitor.state.collectAsState()
    val hvDcap by HvDcapMonitor.state.collectAsState()
    val hvSoce by HvSoceMonitor.state.collectAsState()
    val essCap by EssCapMonitor.state.collectAsState()
    val bcapReady by BcapReadyMonitor.state.collectAsState()
    val essRsrv by EssRsrvMonitor.state.collectAsState()
    val essChgLim by EssChgLimMonitor.state.collectAsState()
    val essChgAct by EssChgActMonitor.state.collectAsState()
    val hvEnerRate by HvEnerRateMonitor.state.collectAsState()
    val hvCurrRate by HvCurrRateMonitor.state.collectAsState()
    val emRpm by EmRpmMonitor.state.collectAsState()
    val emTq by EmTqMonitor.state.collectAsState()
    val fcVolt by FcVoltMonitor.state.collectAsState()
    val fcFuelRate by FcFuelRateMonitor.state.collectAsState()
    val psTrips by PsTripsMonitor.state.collectAsState()
    val hevMode by HevModeMonitor.state.collectAsState()
    val hevBattCurr by HevBattCurrMonitor.state.collectAsState()
    val vSet by VSetMonitor.state.collectAsState()
    val idle by IdleMonitor.state.collectAsState()
    val dtc by DtcMonitor.state.collectAsState()
    val milDist by MilDistanceMonitor.state.collectAsState()
    val distClear by DistSinceClearMonitor.state.collectAsState()
    val parking by ParkingDistanceMonitor.state.collectAsState()
    val doorAjar by DoorAjarMonitor.state.collectAsState()
    val fatigue by ShiftFatigueMonitor.state.collectAsState()
    val restBreak by RestBreakMonitor.state.collectAsState()
    val routeDev by RouteDeviationMonitor.state.collectAsState()
    val driverScore by DriverScoreMonitor.state.collectAsState()
    val ecoLive by EcoLiveMonitor.state.collectAsState()
    val engineRt by EngineRuntimeMonitor.state.collectAsState()
    val hvac by HvacClimateMonitor.state.collectAsState()
    val cabinHot by CabinOvertempMonitor.state.collectAsState()
    val iceFrost by IceFrostMonitor.state.collectAsState()
    val intakeAir by IntakeAirMonitor.state.collectAsState()
    val coolantHot by CoolantOverheatMonitor.state.collectAsState()
    val oilHot by OilTempMonitor.state.collectAsState()
    val catalystHot by CatalystTempMonitor.state.collectAsState()
    val rpmHot by RpmOverRevMonitor.state.collectAsState()
    val engineLoad by EngineLoadMonitor.state.collectAsState()
    val stftTrim by FuelTrimStftMonitor.state.collectAsState()
    val ltftTrim by FuelTrimLtftMonitor.state.collectAsState()
    val mapPress by MapPressureMonitor.state.collectAsState()
    val highThr by HighThrottleMonitor.state.collectAsState()
    val tow by UnauthorizedMoveMonitor.state.collectAsState()
    val pbrake by ParkingBrakeMovingMonitor.state.collectAsState()
    val gearRoll by GearRollMonitor.state.collectAsState()
    val turnStuck by TurnStuckMonitor.state.collectAsState()
    val hazardStuck by HazardStuckMonitor.state.collectAsState()
    val fuelDrop by SuddenFuelDropMonitor.state.collectAsState()
    val tpmsHud by TpmsHudMonitor.state.collectAsState()
    val battV by BatteryVoltageMonitor.state.collectAsState()
    val seatbelt by SeatbeltMonitor.state.collectAsState()
    val harsh by HarshDrivingMonitor.state.collectAsState()
    val impact by ImpactDetectMonitor.state.collectAsState()
    val absHud by AbsHudMonitor.state.collectAsState()
    val panic by PanicBus.state.collectAsState()
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(Unit) { BrandBus.refresh(context) }
    val brand by BrandBus.state.collectAsState()
    LaunchedEffect(Unit) {
        while (true) {
            val snap = com.veplayer.app.vehicle.VehicleState.state.value
            SpeedHudMonitor.tick(prefs, snap.speedKmh)
            MaintenanceMonitor.tick(prefs, snap.odometerKm)
            FuelRangeHudMonitor.tick(prefs, snap.fuelPct, snap.batterySocPct, snap.rangeKm)
            FuelRateMonitor.tick(prefs, snap)
            MafAirflowMonitor.tick(prefs, snap)
            FuelPressureMonitor.tick(prefs, snap)
            BarometricPressureMonitor.tick(prefs, snap)
            TimingAdvanceMonitor.tick(prefs, snap)
            O2VoltageMonitor.tick(prefs, snap)
            AbsoluteLoadMonitor.tick(prefs, snap)
            RelativeThrottleMonitor.tick(prefs, snap)
            AccelPedalMonitor.tick(prefs, snap)
            O2B2VoltageMonitor.tick(prefs, snap)
            EgrErrorMonitor.tick(prefs, snap)
            EquivRatioMonitor.tick(prefs, snap)
            EvapPurgeMonitor.tick(prefs, snap)
            EthanolPctMonitor.tick(prefs, snap)
            EvapVaporMonitor.tick(prefs, snap)
            FuelRailAbsMonitor.tick(prefs, snap)
            CommandedEgrMonitor.tick(prefs, snap)
            RelAccelPedalMonitor.tick(prefs, snap)
            DriverTorqueMonitor.tick(prefs, snap)
            ActualTorqueMonitor.tick(prefs, snap)
            CatalystB2Monitor.tick(prefs, snap)
            CatalystB1S2Monitor.tick(prefs, snap)
            CatalystB2S2Monitor.tick(prefs, snap)
            CatalystB1S3Monitor.tick(prefs, snap)
            CatalystB2S3Monitor.tick(prefs, snap)
            CatalystB1S4Monitor.tick(prefs, snap)
            CatalystB2S4Monitor.tick(prefs, snap)
            FuelTrimStft2B1Monitor.tick(prefs, snap)
            FuelTrimLtft2B1Monitor.tick(prefs, snap)
            FuelTrimStft2B2Monitor.tick(prefs, snap)
            FuelTrimLtft2B2Monitor.tick(prefs, snap)
            CatalystB1S5Monitor.tick(prefs, snap)
            CatalystB2S5Monitor.tick(prefs, snap)
            FuelInjectTimingMonitor.tick(prefs, snap)
            HybridBattLifeMonitor.tick(prefs, snap)
            EngineRefTorqueMonitor.tick(prefs, snap)
            CatalystB1S6Monitor.tick(prefs, snap)
            CatalystB2S6Monitor.tick(prefs, snap)
            ThrottleBMonitor.tick(prefs, snap)
            ThrottleCMonitor.tick(prefs, snap)
            MilTimeOnMonitor.tick(prefs, snap)
            CatalystB1S7Monitor.tick(prefs, snap)
            CatalystB2S7Monitor.tick(prefs, snap)
            FuelTypeMonitor.tick(prefs, snap)
            MaxEquivRatioMonitor.tick(prefs, snap)
            MaxMafGpsMonitor.tick(prefs, snap)
            CatalystB1S8Monitor.tick(prefs, snap)
            CatalystB2S8Monitor.tick(prefs, snap)
            MaxAvailTorqueMonitor.tick(prefs, snap)
            MafSensorIatMonitor.tick(prefs, snap)
            AuxInputStatusMonitor.tick(prefs, snap)
            CatalystB1S9Monitor.tick(prefs, snap)
            CatalystB2S9Monitor.tick(prefs, snap)
            CoolantEct2Monitor.tick(prefs, snap)
            IatSensor2Monitor.tick(prefs, snap)
            TurboInletPressureMonitor.tick(prefs, snap)
            CatalystB1S10Monitor.tick(prefs, snap)
            CatalystB2S10Monitor.tick(prefs, snap)
            EgrTemperatureMonitor.tick(prefs, snap)
            DieselIntakeAirflowMonitor.tick(prefs, snap)
            ThrottleActuatorMonitor.tick(prefs, snap)
            CatalystB1S11Monitor.tick(prefs, snap)
            CatalystB2S11Monitor.tick(prefs, snap)
            ActualEgrMonitor.tick(prefs, snap)
            InjectPressureControlMonitor.tick(prefs, snap)
            FuelPressureControlMonitor.tick(prefs, snap)
            CatalystB1S12Monitor.tick(prefs, snap)
            CatalystB2S12Monitor.tick(prefs, snap)
            FuelTrimStftB2Monitor.tick(prefs, snap)
            FuelTrimLtftB2Monitor.tick(prefs, snap)
            CatalystB1S13Monitor.tick(prefs, snap)
            CatalystB2S13Monitor.tick(prefs, snap)
            DpfAftertreatmentMonitor.tick(prefs, snap)
            ThrottleGMonitor.tick(prefs, snap)
            EngineFrictionTorqueMonitor.tick(prefs, snap)
            CatalystB1S14Monitor.tick(prefs, snap)
            CatalystB2S14Monitor.tick(prefs, snap)
            O2LambdaB1Monitor.tick(prefs, snap)
            PmSensorB1Monitor.tick(prefs, snap)
            PmSensorB2Monitor.tick(prefs, snap)
            EgtB1S5Monitor.tick(prefs, snap)
            EgtB2S5Monitor.tick(prefs, snap)
            O2LambdaB1S3Monitor.tick(prefs, snap)
            O2LambdaB2S3Monitor.tick(prefs, snap)
            NoxReagentQualityMonitor.tick(prefs, snap)
            EgtB1S6Monitor.tick(prefs, snap)
            EgtB2S6Monitor.tick(prefs, snap)
            O2LambdaB1S4Monitor.tick(prefs, snap)
            O2LambdaB2S4Monitor.tick(prefs, snap)
            DefFluidMonitor.tick(prefs, snap)
            EgtB1S7Monitor.tick(prefs, snap)
            EgtB2S7Monitor.tick(prefs, snap)
            EgtB1S8Monitor.tick(prefs, snap)
            EgtB2S8Monitor.tick(prefs, snap)
            O2ConcB1S3Monitor.tick(prefs, snap)
            O2ConcB1S4Monitor.tick(prefs, snap)
            O2ConcB2S3Monitor.tick(prefs, snap)
            O2ConcB2S4Monitor.tick(prefs, snap)
            DefDosingCmdMonitor.tick(prefs, snap)
            NoxCorrectedB1S1Monitor.tick(prefs, snap)
            NoxCorrectedB1S2Monitor.tick(prefs, snap)
            NoxCorrectedB2S1Monitor.tick(prefs, snap)
            NoxCorrectedB2S2Monitor.tick(prefs, snap)
            NoxConcS3Monitor.tick(prefs, snap)
            NoxConcS4Monitor.tick(prefs, snap)
            NoxCorrectedS3Monitor.tick(prefs, snap)
            NoxCorrectedS4Monitor.tick(prefs, snap)
            CylinderFuelRateMonitor.tick(prefs, snap)
            EvapSysVaporMonitor.tick(prefs, snap)
            TransGearRatioMonitor.tick(prefs, snap)
            ObdOdometerMonitor.tick(prefs, snap)
            AbsDisableMonitor.tick(prefs, snap)
            FuelPressAMonitor.tick(prefs, snap)
            FuelPressBMonitor.tick(prefs, snap)
            ReflashDistanceMonitor.tick(prefs, snap)
            FuelLevelInputAMonitor.tick(prefs, snap)
            FuelLevelInputBMonitor.tick(prefs, snap)
            EpcsDiagTimeMonitor.tick(prefs, snap)
            EpcsDiagCountMonitor.tick(prefs, snap)
            NoxPcdLampMonitor.tick(prefs, snap)
            ParticulateInduceWarnMonitor.tick(prefs, snap)
            ParticulateInduceAlertMonitor.tick(prefs, snap)
            DpfRemovalCounterMonitor.tick(prefs, snap)
            ReagentInjectionFailCounterMonitor.tick(prefs, snap)
            ParticulateMonitorMalfunctionCounterMonitor.tick(prefs, snap)
            EngineFuelRateGpsMonitor.tick(prefs, snap)
            EngineExhaustFlowMonitor.tick(prefs, snap)
            FuelSysUsePct1Monitor.tick(prefs, snap)
            FuelSysUsePct2Monitor.tick(prefs, snap)
            FuelSysUsePct3Monitor.tick(prefs, snap)
            WwhObdContinuousMiMonitor.tick(prefs, snap)
            WwhObdEcuB1HoursMonitor.tick(prefs, snap)
            WwhObdCumulativeMiMonitor.tick(prefs, snap)
            FuelSysCtlClosedMonitor.tick(prefs, snap)
            HybridEvBattVoltageMonitor.tick(prefs, snap)
            NoxWarnActiveMonitor.tick(prefs, snap)
            NoxInduceLevel1Monitor.tick(prefs, snap)
            NoxInduceLevel2Monitor.tick(prefs, snap)
            NoxEgrCounterMonitor.tick(prefs, snap)
            NoxMonitorMalfunctionMonitor.tick(prefs, snap)
            HvBattSohMonitor.tick(prefs, snap)
            HvessTempMonitor.tick(prefs, snap)
            HvessCurrentMonitor.tick(prefs, snap)
            HvessPackVoltageMonitor.tick(prefs, snap)
            HvCellMaxTempMonitor.tick(prefs, snap)
            HvBalHoursMonitor.tick(prefs, snap)
            HvCellMinVoltMonitor.tick(prefs, snap)
            HvCellMaxVoltMonitor.tick(prefs, snap)
            HvPwrAvailMonitor.tick(prefs, snap)
            HvChgLimitMonitor.tick(prefs, snap)
            HvCellMinTempMonitor.tick(prefs, snap)
            HvDisLimitMonitor.tick(prefs, snap)
            HvEnrgInMonitor.tick(prefs, snap)
            HvEnrgOutMonitor.tick(prefs, snap)
            HvEnrgTputMonitor.tick(prefs, snap)
            HvAcrMonitor.tick(prefs, snap)
            HvessSohMonitor.tick(prefs, snap)
            HvMinSocMonitor.tick(prefs, snap)
            HvMaxSocMonitor.tick(prefs, snap)
            HvDcapMonitor.tick(prefs, snap)
            HvSoceMonitor.tick(prefs, snap)
            EssCapMonitor.tick(prefs, snap)
            BcapReadyMonitor.tick(prefs, snap)
            EssRsrvMonitor.tick(prefs, snap)
            EssChgLimMonitor.tick(prefs, snap)
            EssChgActMonitor.tick(prefs, snap)
            HvEnerRateMonitor.tick(prefs, snap)
            HvCurrRateMonitor.tick(prefs, snap)
            EmRpmMonitor.tick(prefs, snap)
            EmTqMonitor.tick(prefs, snap)
            FcVoltMonitor.tick(prefs, snap)
            FcFuelRateMonitor.tick(prefs, snap)
            PsTripsMonitor.tick(prefs, snap)
            HevModeMonitor.tick(prefs, snap)
            HevBattCurrMonitor.tick(prefs, snap)
            VSetMonitor.tick(prefs, snap)
            IdleMonitor.tick(prefs, snap.speedKmh, snap.ignition)
            DtcMonitor.tick(prefs, snap)
            MilDistanceMonitor.tick(prefs, snap)
            DistSinceClearMonitor.tick(prefs, snap)
            ParkingDistanceMonitor.tick(prefs, snap.reverse)
            DoorAjarMonitor.tick(prefs, snap)
            SeatbeltMonitor.tick(prefs, snap)
            HarshDrivingMonitor.tick(prefs, snap)
            ImpactDetectMonitor.tick(prefs, snap)
            AbsHudMonitor.tick(prefs, snap)
            ShiftFatigueMonitor.tick(prefs)
            RestBreakMonitor.tick(prefs, snap)
            RouteDeviationMonitor.tick(prefs)
            DriverScoreMonitor.tick(prefs)
            EcoLiveMonitor.tick(prefs)
            EngineRuntimeMonitor.tick(prefs, snap)
            HvacClimateMonitor.tick(prefs, snap)
            CabinOvertempMonitor.tick(prefs, snap)
            IceFrostMonitor.tick(prefs, snap)
            IntakeAirMonitor.tick(prefs, snap)
            CoolantOverheatMonitor.tick(prefs, snap)
            OilTempMonitor.tick(prefs, snap)
            CatalystTempMonitor.tick(prefs, snap)
            RpmOverRevMonitor.tick(prefs, snap)
            EngineLoadMonitor.tick(prefs, snap)
            FuelTrimStftMonitor.tick(prefs, snap)
            FuelTrimLtftMonitor.tick(prefs, snap)
            MapPressureMonitor.tick(prefs, snap)
            HighThrottleMonitor.tick(prefs, snap)
            UnauthorizedMoveMonitor.tick(prefs, snap)
            ParkingBrakeMovingMonitor.tick(prefs, snap)
            GearRollMonitor.tick(prefs, snap)
            TurnStuckMonitor.tick(prefs, snap)
            HazardStuckMonitor.tick(prefs, snap)
            SuddenFuelDropMonitor.tick(prefs, snap)
            TpmsHudMonitor.tick(prefs, snap)
            BatteryVoltageMonitor.tick(prefs, snap)
            delay(500)
        }
    }
    val driverLabel =
        if (prefs.driverId > 0) {
            prefs.driverName.ifBlank { prefs.driverCode }
        } else {
            ""
        }
    val speedColor = Color(SpeedHud.accentArgb(hud.band))

    Column(
        modifier = modifier
            .background(Night)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                when {
                    vehicle.reverse -> "R"
                    vehicle.gear == Gear.P -> "P"
                    vehicle.gear == Gear.N -> "N"
                    else -> vehicle.speedKmh.toInt().toString()
                },
                color = if (vehicle.reverse || vehicle.gear == Gear.P || vehicle.gear == Gear.N) Mist else speedColor,
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.padding(bottom = 10.dp).weight(1f)) {
                Text(
                    when {
                        vehicle.reverse -> "REVERSE"
                        idle.showWarn -> IdleAlert.labelLine(idle)
                        idle.band == "idle" && prefs.idleAlertEnabled -> IdleAlert.labelLine(idle)
                        vehicle.gear == Gear.P -> "PARK"
                        vehicle.gear == Gear.N -> "NEUTRAL"
                        hud.showWarn -> "OVER · +${hud.overBy.toInt()}"
                        else -> "km/h"
                    },
                    color =
                        when {
                            hud.showWarn -> speedColor
                            idle.showWarn || idle.band == "idle" -> Color(IdleAlert.accentArgb(idle.band))
                            else -> Mute
                        },
                    fontSize = 18.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TurnChip("◀", vehicle.turn == TurnSignal.LEFT || vehicle.turn == TurnSignal.HAZARD)
                    TurnChip("▶", vehicle.turn == TurnSignal.RIGHT || vehicle.turn == TurnSignal.HAZARD)
                    Text(vehicle.source, color = Mute, fontSize = 11.sp)
                }
                if (driverLabel.isNotBlank()) {
                    Text("Conductor · $driverLabel", color = Mute, fontSize = 11.sp)
                }
                val shift by com.veplayer.app.fleet.ShiftTracker.shift.collectAsState()
                val shiftSum by com.veplayer.app.fleet.ShiftTracker.summary.collectAsState()
                if (shift.status == "open" || fatigue.open) {
                    Text(
                        buildString {
                            append("Turno")
                            if (fatigue.label.isNotBlank()) append(" · ${fatigue.label}")
                            if (shift.status == "open") {
                                append(" · ${"%.1f".format(shift.distanceKm)} km")
                            }
                        },
                        color =
                            if (fatigue.showWarn) {
                                Color(com.veplayer.app.vehicle.ShiftFatigue.accentArgb(fatigue.band))
                            } else {
                                Mute
                            },
                        fontSize = 11.sp,
                    )
                } else if (prefs.shiftSummaryEnabled && shiftSum.show) {
                    Text(
                        shiftSum.label,
                        color = Color(com.veplayer.app.fleet.ShiftSummary.accentArgb()),
                        fontSize = 11.sp,
                    )
                }
                if (restBreak.showWarn || (prefs.restBreakEnabled && restBreak.band == "ok" && restBreak.drivingSec >= 600f)) {
                    Text(
                        restBreak.label,
                        color = Color(com.veplayer.app.vehicle.RestBreak.accentArgb(restBreak.band)),
                        fontSize = 11.sp,
                    )
                }
                if (routeDev.showWarn || (prefs.routeDevEnabled && routeDev.hasRoute && routeDev.band == "ok" && routeDev.distanceM >= 15f)) {
                    Text(
                        routeDev.label,
                        color = Color(com.veplayer.app.vehicle.RouteDeviation.accentArgb(routeDev.band)),
                        fontSize = 11.sp,
                    )
                }
                if (prefs.maintenanceEnabled && (maint.due > 0 || maint.warn > 0)) {
                    val tip =
                        maint.items.firstOrNull { it.band == "due" || it.band == "warn" }
                    Text(
                        when {
                            tip?.band == "due" -> "Mant · ${tip.item.label} vencido"
                            tip != null ->
                                "Mant · ${tip.item.label} ${tip.remainingKm?.toInt() ?: "?"} km"
                            else -> "Mant · ${maint.due + maint.warn}"
                        },
                        color = Mute,
                        fontSize = 11.sp,
                    )
                }
                val shiftHud by com.veplayer.app.fleet.ShiftTracker.shift.collectAsState()
                if (ecoLive.active && prefs.ecoLiveEnabled) {
                    Text(
                        ecoLive.label,
                        color = Color(com.veplayer.app.vehicle.EcoScore.accentArgb(ecoLive.band)),
                        fontSize = 11.sp,
                    )
                } else if (shiftHud.status == "open" && shiftHud.ecoScore != null) {
                    Text(
                        "Eco ${shiftHud.ecoScore} · ${shiftHud.ecoBand}",
                        color = Color(com.veplayer.app.vehicle.EcoScore.accentArgb(shiftHud.ecoBand)),
                        fontSize = 11.sp,
                    )
                }
                if (engineRt.showWarn || (prefs.engineRuntimeEnabled && engineRt.band == "ok")) {
                    Text(
                        engineRt.label,
                        color = Color(com.veplayer.app.vehicle.EngineRuntime.accentArgb(engineRt.band)),
                        fontSize = 11.sp,
                    )
                }
                if (driverScore.active && (prefs.driverScoreEnabled)) {
                    Text(
                        driverScore.label,
                        color = Color(com.veplayer.app.vehicle.DriverScore.accentArgb(driverScore.band)),
                        fontSize = 11.sp,
                    )
                }
                val phone by com.veplayer.app.phone.PhoneLinkBus.state.collectAsState()
                if (phone.connected) {
                    Text(
                        when (phone.protocol) {
                            com.veplayer.app.phone.PhoneLinkBus.Protocol.ANDROID_AUTO -> "AA · ${phone.deviceName.take(14)}"
                            com.veplayer.app.phone.PhoneLinkBus.Protocol.CARPLAY -> "CarPlay · ${phone.deviceName.take(12)}"
                            else -> "Phone · ${phone.deviceName.take(14)}"
                        },
                        color = Color(0xFF14B8A6),
                        fontSize = 11.sp,
                    )
                }
                if (parking.active && parking.label.isNotBlank()) {
                    Text(
                        "PDC · ${parking.label}",
                        color = Color(com.veplayer.app.vehicle.ParkingDistance.accentArgb(parking.band)),
                        fontSize = 11.sp,
                    )
                }
                if (doorAjar.label.isNotBlank()) {
                    Text(
                        "Puerta · ${doorAjar.label}",
                        color = Color(com.veplayer.app.vehicle.DoorAjar.accentArgb(doorAjar.band)),
                        fontSize = 11.sp,
                    )
                }
                if (seatbelt.label.isNotBlank()) {
                    Text(
                        seatbelt.label,
                        color = Color(com.veplayer.app.vehicle.Seatbelt.accentArgb(seatbelt.band)),
                        fontSize = 11.sp,
                    )
                }
                if (harsh.showWarn) {
                    Text(
                        harsh.label,
                        color = Color(com.veplayer.app.vehicle.HarshDriving.accentArgb(harsh.band)),
                        fontSize = 11.sp,
                    )
                }
                if (impact.showWarn) {
                    Text(
                        impact.label,
                        color = Color(com.veplayer.app.vehicle.ImpactDetect.accentArgb(impact.band)),
                        fontSize = 11.sp,
                    )
                }
                if (absHud.showWarn || (prefs.absHudEnabled && absHud.active)) {
                    Text(
                        absHud.label.ifBlank { "ABS" },
                        color = Color(com.veplayer.app.vehicle.AbsHud.accentArgb(absHud.band)),
                        fontSize = 11.sp,
                    )
                }
                if (cabinHot.showWarn) {
                    Text(
                        "Cabina · ${cabinHot.label}",
                        color = Color(com.veplayer.app.vehicle.CabinOvertemp.accentArgb(cabinHot.band)),
                        fontSize = 11.sp,
                    )
                }
                if (iceFrost.showWarn || (prefs.iceEnabled && iceFrost.band == "ok")) {
                    Text(
                        "Ext · ${iceFrost.label}",
                        color = Color(com.veplayer.app.vehicle.IceFrost.accentArgb(iceFrost.band)),
                        fontSize = 11.sp,
                    )
                }
                if (intakeAir.showWarn || (prefs.intakeAirEnabled && intakeAir.band == "ok" && (intakeAir.intakeAirC ?: 0f) >= 40f)) {
                    Text(
                        "IAT · ${intakeAir.label}",
                        color = Color(com.veplayer.app.vehicle.IntakeAir.accentArgb(intakeAir.band)),
                        fontSize = 11.sp,
                    )
                }
                if (coolantHot.showWarn) {
                    Text(
                        "Motor · ${coolantHot.label}",
                        color = Color(com.veplayer.app.vehicle.CoolantOverheat.accentArgb(coolantHot.band)),
                        fontSize = 11.sp,
                    )
                }
                if (oilHot.showWarn || (prefs.oilTempEnabled && oilHot.band == "ok" && (oilHot.oilTempC ?: 0f) >= 100f)) {
                    Text(
                        "Aceite · ${oilHot.label}",
                        color = Color(com.veplayer.app.vehicle.OilTemp.accentArgb(oilHot.band)),
                        fontSize = 11.sp,
                    )
                }
                if (
                    catalystHot.showWarn ||
                        (
                            prefs.catalystEnabled &&
                                catalystHot.band == "ok" &&
                                (catalystHot.catalystTempC ?: 0f) >= 500f
                        )
                ) {
                    Text(
                        catalystHot.label,
                        color = Color(com.veplayer.app.vehicle.CatalystTemp.accentArgb(catalystHot.band)),
                        fontSize = 11.sp,
                    )
                }
                if (rpmHot.showWarn || (prefs.rpmEnabled && rpmHot.band == "ok" && (rpmHot.rpm ?: 0f) >= 2000f)) {
                    Text(
                        "RPM · ${rpmHot.label}",
                        color = Color(com.veplayer.app.vehicle.RpmOverRev.accentArgb(rpmHot.band)),
                        fontSize = 11.sp,
                    )
                }
                if (engineLoad.showWarn || (prefs.engineLoadEnabled && engineLoad.band == "ok" && (engineLoad.loadPct ?: 0f) >= 55f)) {
                    Text(
                        engineLoad.label,
                        color = Color(com.veplayer.app.vehicle.EngineLoad.accentArgb(engineLoad.band)),
                        fontSize = 11.sp,
                    )
                }
                if (
                    stftTrim.showWarn ||
                        (
                            prefs.stftEnabled &&
                                stftTrim.band == "ok" &&
                                kotlin.math.abs(stftTrim.trimPct ?: 0f) >= 8f &&
                                stftTrim.label.isNotBlank()
                        )
                ) {
                    Text(
                        stftTrim.label,
                        color = Color(com.veplayer.app.vehicle.FuelTrimStft.accentArgb(stftTrim.band)),
                        fontSize = 11.sp,
                    )
                }
                if (
                    ltftTrim.showWarn ||
                        (
                            prefs.ltftEnabled &&
                                ltftTrim.band == "ok" &&
                                kotlin.math.abs(ltftTrim.trimPct ?: 0f) >= 8f &&
                                ltftTrim.label.isNotBlank()
                        )
                ) {
                    Text(
                        ltftTrim.label,
                        color = Color(com.veplayer.app.vehicle.FuelTrimLtft.accentArgb(ltftTrim.band)),
                        fontSize = 11.sp,
                    )
                }
                if (
                    mapPress.showWarn ||
                        (
                            prefs.mapEnabled &&
                                mapPress.band == "ok" &&
                                (mapPress.mapKpa ?: 0f) >= 70f &&
                                mapPress.label.isNotBlank()
                        )
                ) {
                    Text(
                        mapPress.label,
                        color = Color(com.veplayer.app.vehicle.MapPressure.accentArgb(mapPress.band)),
                        fontSize = 11.sp,
                    )
                }
                if (highThr.showWarn || (prefs.throttleEnabled && highThr.band == "ok" && (highThr.throttlePct ?: 0f) >= 50f)) {
                    Text(
                        highThr.label,
                        color = Color(com.veplayer.app.vehicle.HighThrottle.accentArgb(highThr.band)),
                        fontSize = 11.sp,
                    )
                }
                if (tow.showWarn || tow.band == "moving") {
                    Text(
                        tow.label.ifBlank { "Remolque" },
                        color = Color(com.veplayer.app.vehicle.UnauthorizedMove.accentArgb(tow.band)),
                        fontSize = 11.sp,
                    )
                }
                if (pbrake.showWarn) {
                    Text(
                        pbrake.label,
                        color = Color(com.veplayer.app.vehicle.ParkingBrakeMoving.accentArgb(pbrake.band)),
                        fontSize = 11.sp,
                    )
                }
                if (gearRoll.showWarn) {
                    Text(
                        gearRoll.label,
                        color = Color(com.veplayer.app.vehicle.GearRoll.accentArgb(gearRoll.band)),
                        fontSize = 11.sp,
                    )
                }
                if (turnStuck.showWarn || (prefs.turnStuckEnabled && turnStuck.band == "ok")) {
                    Text(
                        turnStuck.label,
                        color = Color(com.veplayer.app.vehicle.TurnStuck.accentArgb(turnStuck.band)),
                        fontSize = 11.sp,
                    )
                }
                if (hazardStuck.showWarn || (prefs.hazardStuckEnabled && hazardStuck.band == "ok")) {
                    Text(
                        hazardStuck.label,
                        color = Color(com.veplayer.app.vehicle.HazardStuck.accentArgb(hazardStuck.band)),
                        fontSize = 11.sp,
                    )
                }
                if (fuelDrop.showWarn) {
                    Text(
                        "Combustible · ${fuelDrop.label}",
                        color = Color(com.veplayer.app.vehicle.SuddenFuelDrop.accentArgb(fuelDrop.band)),
                        fontSize = 11.sp,
                    )
                }
                if (fuelRate.showWarn || (prefs.fuelRateEnabled && fuelRate.band == "ok" && (fuelRate.fuelRateLph ?: 0f) >= 35f)) {
                    Text(
                        fuelRate.label,
                        color = Color(com.veplayer.app.vehicle.FuelRate.accentArgb(fuelRate.band)),
                        fontSize = 11.sp,
                    )
                }
                if (
                    mafFlow.showWarn ||
                        (
                            prefs.mafEnabled &&
                                mafFlow.band == "ok" &&
                                (mafFlow.mafGps ?: 0f) >= 25f &&
                                mafFlow.label.isNotBlank()
                        )
                ) {
                    Text(
                        mafFlow.label,
                        color = Color(com.veplayer.app.vehicle.MafAirflow.accentArgb(mafFlow.band)),
                        fontSize = 11.sp,
                    )
                }
                if (
                    fuelPress.showWarn ||
                        (
                            prefs.fuelPressEnabled &&
                                fuelPress.band == "ok" &&
                                (fuelPress.pressureKpa ?: 999f) <= 350f &&
                                fuelPress.label.isNotBlank()
                        )
                ) {
                    Text(
                        fuelPress.label,
                        color = Color(com.veplayer.app.vehicle.FuelPressure.accentArgb(fuelPress.band)),
                        fontSize = 11.sp,
                    )
                }
                if (
                    baroPress.showWarn ||
                        (
                            prefs.baroEnabled &&
                                baroPress.band == "ok" &&
                                baroPress.label.isNotBlank()
                        )
                ) {
                    Text(
                        baroPress.label,
                        color = Color(com.veplayer.app.vehicle.BarometricPressure.accentArgb(baroPress.band)),
                        fontSize = 11.sp,
                    )
                }
                if (
                    timingAdv.showWarn ||
                        (
                            prefs.timingEnabled &&
                                timingAdv.band == "ok" &&
                                timingAdv.label.isNotBlank()
                        )
                ) {
                    Text(
                        timingAdv.label,
                        color = Color(com.veplayer.app.vehicle.TimingAdvance.accentArgb(timingAdv.band)),
                        fontSize = 11.sp,
                    )
                }
                if (
                    o2Volt.showWarn ||
                        (
                            prefs.o2Enabled &&
                                o2Volt.band == "ok" &&
                                o2Volt.label.isNotBlank()
                        )
                ) {
                    Text(
                        o2Volt.label,
                        color = Color(com.veplayer.app.vehicle.O2Voltage.accentArgb(o2Volt.band)),
                        fontSize = 11.sp,
                    )
                }
                if (absLoad.showWarn || (prefs.absLoadEnabled && absLoad.band == "ok" && absLoad.label.isNotBlank())) {
                    Text(
                        absLoad.label,
                        color = Color(com.veplayer.app.vehicle.AbsoluteLoad.accentArgb(absLoad.band)),
                        fontSize = 11.sp,
                    )
                }
                if (relThr.showWarn || (prefs.relThrEnabled && relThr.band == "ok" && relThr.label.isNotBlank())) {
                    Text(
                        relThr.label,
                        color = Color(com.veplayer.app.vehicle.RelativeThrottle.accentArgb(relThr.band)),
                        fontSize = 11.sp,
                    )
                }
                if (accelPedal.showWarn || (prefs.accelPedalEnabled && accelPedal.band == "ok" && accelPedal.label.isNotBlank())) {
                    Text(
                        accelPedal.label,
                        color = Color(com.veplayer.app.vehicle.AccelPedal.accentArgb(accelPedal.band)),
                        fontSize = 11.sp,
                    )
                }
                if (o2B2.showWarn || (prefs.o2B2Enabled && o2B2.band == "ok" && o2B2.label.isNotBlank())) {
                    Text(
                        o2B2.label,
                        color = Color(com.veplayer.app.vehicle.O2B2Voltage.accentArgb(o2B2.band)),
                        fontSize = 11.sp,
                    )
                }
                if (egrErr.showWarn || (prefs.egrEnabled && egrErr.band == "ok" && egrErr.label.isNotBlank())) {
                    Text(
                        egrErr.label,
                        color = Color(com.veplayer.app.vehicle.EgrError.accentArgb(egrErr.band)),
                        fontSize = 11.sp,
                    )
                }
                if (equiv.showWarn || (prefs.equivEnabled && equiv.band == "ok" && equiv.label.isNotBlank())) {
                    Text(equiv.label, color = Color(com.veplayer.app.vehicle.EquivRatio.accentArgb(equiv.band)), fontSize = 11.sp)
                }
                if (evapPur.showWarn || (prefs.evapPurgeEnabled && evapPur.band == "ok" && evapPur.label.isNotBlank())) {
                    Text(evapPur.label, color = Color(com.veplayer.app.vehicle.EvapPurge.accentArgb(evapPur.band)), fontSize = 11.sp)
                }
                if (ethanol.showWarn || (prefs.ethanolEnabled && ethanol.band == "ok" && ethanol.label.isNotBlank())) {
                    Text(ethanol.label, color = Color(com.veplayer.app.vehicle.EthanolPct.accentArgb(ethanol.band)), fontSize = 11.sp)
                }
                if (evapVap.showWarn || (prefs.evapVaporEnabled && evapVap.band == "ok" && evapVap.label.isNotBlank())) {
                    Text(evapVap.label, color = Color(com.veplayer.app.vehicle.EvapVapor.accentArgb(evapVap.band)), fontSize = 11.sp)
                }
                if (railAbs.showWarn || (prefs.railAbsEnabled && railAbs.band == "ok" && railAbs.label.isNotBlank())) {
                    Text(railAbs.label, color = Color(com.veplayer.app.vehicle.FuelRailAbs.accentArgb(railAbs.band)), fontSize = 11.sp)
                }
                if (egrCmd.showWarn || (prefs.egrCmdEnabled && egrCmd.band == "ok" && egrCmd.label.isNotBlank())) {
                    Text(egrCmd.label, color = Color(com.veplayer.app.vehicle.CommandedEgr.accentArgb(egrCmd.band)), fontSize = 11.sp)
                }
                if (relAped.showWarn || (prefs.relApedEnabled && relAped.band == "ok" && relAped.label.isNotBlank())) {
                    Text(relAped.label, color = Color(com.veplayer.app.vehicle.RelAccelPedal.accentArgb(relAped.band)), fontSize = 11.sp)
                }
                if (drvTorque.showWarn || (prefs.drvTorqueEnabled && drvTorque.band == "ok" && drvTorque.label.isNotBlank())) {
                    Text(drvTorque.label, color = Color(com.veplayer.app.vehicle.DriverTorque.accentArgb(drvTorque.band)), fontSize = 11.sp)
                }
                if (actTorque.showWarn || (prefs.actTorqueEnabled && actTorque.band == "ok" && actTorque.label.isNotBlank())) {
                    Text(actTorque.label, color = Color(com.veplayer.app.vehicle.ActualTorque.accentArgb(actTorque.band)), fontSize = 11.sp)
                }
                if (catB2.showWarn || (prefs.catB2Enabled && catB2.band == "ok" && catB2.label.isNotBlank())) {
                    Text(catB2.label, color = Color(com.veplayer.app.vehicle.CatalystB2.accentArgb(catB2.band)), fontSize = 11.sp)
                }
                if (catB1s2.showWarn || (prefs.catB1s2Enabled && catB1s2.band == "ok" && catB1s2.label.isNotBlank())) {
                    Text(catB1s2.label, color = Color(com.veplayer.app.vehicle.CatalystB1S2.accentArgb(catB1s2.band)), fontSize = 11.sp)
                }
                if (catB2s2.showWarn || (prefs.catB2s2Enabled && catB2s2.band == "ok" && catB2s2.label.isNotBlank())) {
                    Text(catB2s2.label, color = Color(com.veplayer.app.vehicle.CatalystB2S2.accentArgb(catB2s2.band)), fontSize = 11.sp)
                }
                if (catB1s3.showWarn || (prefs.catB1s3Enabled && catB1s3.band == "ok" && catB1s3.label.isNotBlank())) {
                    Text(catB1s3.label, color = Color(com.veplayer.app.vehicle.CatalystB1S3.accentArgb(catB1s3.band)), fontSize = 11.sp)
                }
                if (catB2s3.showWarn || (prefs.catB2s3Enabled && catB2s3.band == "ok" && catB2s3.label.isNotBlank())) {
                    Text(catB2s3.label, color = Color(com.veplayer.app.vehicle.CatalystB2S3.accentArgb(catB2s3.band)), fontSize = 11.sp)
                }
                if (catB1s4.showWarn || (prefs.catB1s4Enabled && catB1s4.band == "ok" && catB1s4.label.isNotBlank())) {
                    Text(catB1s4.label, color = Color(com.veplayer.app.vehicle.CatalystB1S4.accentArgb(catB1s4.band)), fontSize = 11.sp)
                }
                if (catB2s4.showWarn || (prefs.catB2s4Enabled && catB2s4.band == "ok" && catB2s4.label.isNotBlank())) {
                    Text(catB2s4.label, color = Color(com.veplayer.app.vehicle.CatalystB2S4.accentArgb(catB2s4.band)), fontSize = 11.sp)
                }
                if (stft2B1.showWarn || (prefs.stft2B1Enabled && stft2B1.band == "ok" && stft2B1.label.isNotBlank())) {
                    Text(stft2B1.label, color = Color(com.veplayer.app.vehicle.FuelTrimStft2B1.accentArgb(stft2B1.band)), fontSize = 11.sp)
                }
                if (ltft2B1.showWarn || (prefs.ltft2B1Enabled && ltft2B1.band == "ok" && ltft2B1.label.isNotBlank())) {
                    Text(ltft2B1.label, color = Color(com.veplayer.app.vehicle.FuelTrimLtft2B1.accentArgb(ltft2B1.band)), fontSize = 11.sp)
                }
                if (stft2B2.showWarn || (prefs.stft2B2Enabled && stft2B2.band == "ok" && stft2B2.label.isNotBlank())) {
                    Text(stft2B2.label, color = Color(com.veplayer.app.vehicle.FuelTrimStft2B2.accentArgb(stft2B2.band)), fontSize = 11.sp)
                }
                if (ltft2B2.showWarn || (prefs.ltft2B2Enabled && ltft2B2.band == "ok" && ltft2B2.label.isNotBlank())) {
                    Text(ltft2B2.label, color = Color(com.veplayer.app.vehicle.FuelTrimLtft2B2.accentArgb(ltft2B2.band)), fontSize = 11.sp)
                }
                if (catB1s5.showWarn || (prefs.catB1s5Enabled && catB1s5.band == "ok" && catB1s5.label.isNotBlank())) {
                    Text(catB1s5.label, color = Color(com.veplayer.app.vehicle.CatalystB1S5.accentArgb(catB1s5.band)), fontSize = 11.sp)
                }
                if (catB2s5.showWarn || (prefs.catB2s5Enabled && catB2s5.band == "ok" && catB2s5.label.isNotBlank())) {
                    Text(catB2s5.label, color = Color(com.veplayer.app.vehicle.CatalystB2S5.accentArgb(catB2s5.band)), fontSize = 11.sp)
                }
                if (inject.showWarn || (prefs.injectEnabled && inject.band == "ok" && inject.label.isNotBlank())) {
                    Text(inject.label, color = Color(com.veplayer.app.vehicle.FuelInjectTiming.accentArgb(inject.band)), fontSize = 11.sp)
                }
                if (hybridBatt.showWarn || (prefs.hybridEnabled && hybridBatt.band == "ok" && hybridBatt.label.isNotBlank())) {
                    Text(hybridBatt.label, color = Color(com.veplayer.app.vehicle.HybridBattLife.accentArgb(hybridBatt.band)), fontSize = 11.sp)
                }
                if (refTorque.showWarn || (prefs.refTorqueEnabled && refTorque.band == "ok" && refTorque.label.isNotBlank())) {
                    Text(refTorque.label, color = Color(com.veplayer.app.vehicle.EngineRefTorque.accentArgb(refTorque.band)), fontSize = 11.sp)
                }
                if (catB1s6.showWarn || (prefs.catB1s6Enabled && catB1s6.band == "ok" && catB1s6.label.isNotBlank())) {
                    Text(catB1s6.label, color = Color(com.veplayer.app.vehicle.CatalystB1S6.accentArgb(catB1s6.band)), fontSize = 11.sp)
                }
                if (catB2s6.showWarn || (prefs.catB2s6Enabled && catB2s6.band == "ok" && catB2s6.label.isNotBlank())) {
                    Text(catB2s6.label, color = Color(com.veplayer.app.vehicle.CatalystB2S6.accentArgb(catB2s6.band)), fontSize = 11.sp)
                }
                if (thrB.showWarn || (prefs.thrBEnabled && thrB.band == "ok" && thrB.label.isNotBlank())) {
                    Text(thrB.label, color = Color(com.veplayer.app.vehicle.ThrottleB.accentArgb(thrB.band)), fontSize = 11.sp)
                }
                if (thrC.showWarn || (prefs.thrCEnabled && thrC.band == "ok" && thrC.label.isNotBlank())) {
                    Text(thrC.label, color = Color(com.veplayer.app.vehicle.ThrottleC.accentArgb(thrC.band)), fontSize = 11.sp)
                }
                if (milTime.showWarn || (prefs.milTimeEnabled && milTime.milOn && milTime.label.isNotBlank())) {
                    Text(milTime.label, color = Color(com.veplayer.app.vehicle.MilTimeOn.accentArgb(milTime.band)), fontSize = 11.sp)
                }
                if (catB1s7.showWarn || (prefs.catB1s7Enabled && catB1s7.band == "ok" && catB1s7.label.isNotBlank())) {
                    Text(catB1s7.label, color = Color(com.veplayer.app.vehicle.CatalystB1S7.accentArgb(catB1s7.band)), fontSize = 11.sp)
                }
                if (catB2s7.showWarn || (prefs.catB2s7Enabled && catB2s7.band == "ok" && catB2s7.label.isNotBlank())) {
                    Text(catB2s7.label, color = Color(com.veplayer.app.vehicle.CatalystB2S7.accentArgb(catB2s7.band)), fontSize = 11.sp)
                }
                if (fuelType.showWarn || (prefs.fuelTypeEnabled && fuelType.label.isNotBlank())) {
                    Text(fuelType.label, color = Color(com.veplayer.app.vehicle.FuelType.accentArgb(fuelType.band)), fontSize = 11.sp)
                }
                if (maxEquiv.showWarn || (prefs.maxEquivEnabled && maxEquiv.band == "ok" && maxEquiv.label.isNotBlank())) {
                    Text(maxEquiv.label, color = Color(com.veplayer.app.vehicle.MaxEquivRatio.accentArgb(maxEquiv.band)), fontSize = 11.sp)
                }
                if (maxMaf.showWarn || (prefs.maxMafEnabled && maxMaf.band == "ok" && maxMaf.label.isNotBlank())) {
                    Text(maxMaf.label, color = Color(com.veplayer.app.vehicle.MaxMafGps.accentArgb(maxMaf.band)), fontSize = 11.sp)
                }
                if (catB1s8.showWarn || (prefs.catB1s8Enabled && catB1s8.band == "ok" && catB1s8.label.isNotBlank())) {
                    Text(catB1s8.label, color = Color(com.veplayer.app.vehicle.CatalystB1S8.accentArgb(catB1s8.band)), fontSize = 11.sp)
                }
                if (catB2s8.showWarn || (prefs.catB2s8Enabled && catB2s8.band == "ok" && catB2s8.label.isNotBlank())) {
                    Text(catB2s8.label, color = Color(com.veplayer.app.vehicle.CatalystB2S8.accentArgb(catB2s8.band)), fontSize = 11.sp)
                }
                if (maxAvailTorque.showWarn || (prefs.maxAvailTorqueEnabled && maxAvailTorque.band == "ok" && maxAvailTorque.label.isNotBlank())) {
                    Text(maxAvailTorque.label, color = Color(com.veplayer.app.vehicle.MaxAvailTorque.accentArgb(maxAvailTorque.band)), fontSize = 11.sp)
                }
                if (mafIat.showWarn || (prefs.mafIatEnabled && mafIat.band == "ok" && mafIat.label.isNotBlank())) {
                    Text(mafIat.label, color = Color(com.veplayer.app.vehicle.MafSensorIat.accentArgb(mafIat.band)), fontSize = 11.sp)
                }
                if (auxInput.showWarn || (prefs.auxInputEnabled && auxInput.label.isNotBlank())) {
                    Text(auxInput.label, color = Color(com.veplayer.app.vehicle.AuxInputStatus.accentArgb(auxInput.band)), fontSize = 11.sp)
                }
                if (catB1s9.showWarn || (prefs.catB1s9Enabled && catB1s9.band == "ok" && catB1s9.label.isNotBlank())) {
                    Text(catB1s9.label, color = Color(com.veplayer.app.vehicle.CatalystB1S9.accentArgb(catB1s9.band)), fontSize = 11.sp)
                }
                if (catB2s9.showWarn || (prefs.catB2s9Enabled && catB2s9.band == "ok" && catB2s9.label.isNotBlank())) {
                    Text(catB2s9.label, color = Color(com.veplayer.app.vehicle.CatalystB2S9.accentArgb(catB2s9.band)), fontSize = 11.sp)
                }
                if (ect2.showWarn || (prefs.ect2Enabled && ect2.band == "ok" && ect2.label.isNotBlank())) {
                    Text(ect2.label, color = Color(com.veplayer.app.vehicle.CoolantEct2.accentArgb(ect2.band)), fontSize = 11.sp)
                }
                if (iat2.showWarn || (prefs.iat2Enabled && iat2.band == "ok" && iat2.label.isNotBlank())) {
                    Text(iat2.label, color = Color(com.veplayer.app.vehicle.IatSensor2.accentArgb(iat2.band)), fontSize = 11.sp)
                }
                if (turboInlet.showWarn || (prefs.turboInletEnabled && turboInlet.band == "ok" && turboInlet.label.isNotBlank())) {
                    Text(turboInlet.label, color = Color(com.veplayer.app.vehicle.TurboInletPressure.accentArgb(turboInlet.band)), fontSize = 11.sp)
                }
                if (catB1s10.showWarn || (prefs.catB1s10Enabled && catB1s10.band == "ok" && catB1s10.label.isNotBlank())) {
                    Text(catB1s10.label, color = Color(com.veplayer.app.vehicle.CatalystB1S10.accentArgb(catB1s10.band)), fontSize = 11.sp)
                }
                if (catB2s10.showWarn || (prefs.catB2s10Enabled && catB2s10.band == "ok" && catB2s10.label.isNotBlank())) {
                    Text(catB2s10.label, color = Color(com.veplayer.app.vehicle.CatalystB2S10.accentArgb(catB2s10.band)), fontSize = 11.sp)
                }
                if (egrTemp.showWarn || (prefs.egrTempEnabled && egrTemp.band == "ok" && egrTemp.label.isNotBlank())) {
                    Text(egrTemp.label, color = Color(com.veplayer.app.vehicle.EgrTemperature.accentArgb(egrTemp.band)), fontSize = 11.sp)
                }
                if (dieselIaf.showWarn || (prefs.dieselIafEnabled && dieselIaf.band == "ok" && dieselIaf.label.isNotBlank())) {
                    Text(dieselIaf.label, color = Color(com.veplayer.app.vehicle.DieselIntakeAirflow.accentArgb(dieselIaf.band)), fontSize = 11.sp)
                }
                if (thrAct.showWarn || (prefs.thrActEnabled && thrAct.band == "ok" && thrAct.label.isNotBlank())) {
                    Text(thrAct.label, color = Color(com.veplayer.app.vehicle.ThrottleActuator.accentArgb(thrAct.band)), fontSize = 11.sp)
                }
                if (catB1s11.showWarn || (prefs.catB1s11Enabled && catB1s11.band == "ok" && catB1s11.label.isNotBlank())) {
                    Text(catB1s11.label, color = Color(com.veplayer.app.vehicle.CatalystB1S11.accentArgb(catB1s11.band)), fontSize = 11.sp)
                }
                if (catB2s11.showWarn || (prefs.catB2s11Enabled && catB2s11.band == "ok" && catB2s11.label.isNotBlank())) {
                    Text(catB2s11.label, color = Color(com.veplayer.app.vehicle.CatalystB2S11.accentArgb(catB2s11.band)), fontSize = 11.sp)
                }
                if (egrActual.showWarn || (prefs.egrActualEnabled && egrActual.band == "ok" && egrActual.label.isNotBlank())) {
                    Text(egrActual.label, color = Color(com.veplayer.app.vehicle.ActualEgr.accentArgb(egrActual.band)), fontSize = 11.sp)
                }
                if (injectCtrl.showWarn || (prefs.injectCtrlEnabled && injectCtrl.band == "ok" && injectCtrl.label.isNotBlank())) {
                    Text(injectCtrl.label, color = Color(com.veplayer.app.vehicle.InjectPressureControl.accentArgb(injectCtrl.band)), fontSize = 11.sp)
                }
                if (fuelCtrl.showWarn || (prefs.fuelCtrlEnabled && fuelCtrl.band == "ok" && fuelCtrl.label.isNotBlank())) {
                    Text(fuelCtrl.label, color = Color(com.veplayer.app.vehicle.FuelPressureControl.accentArgb(fuelCtrl.band)), fontSize = 11.sp)
                }
                if (catB1s12.showWarn || (prefs.catB1s12Enabled && catB1s12.band == "ok" && catB1s12.label.isNotBlank())) {
                    Text(catB1s12.label, color = Color(com.veplayer.app.vehicle.CatalystB1S12.accentArgb(catB1s12.band)), fontSize = 11.sp)
                }
                if (catB2s12.showWarn || (prefs.catB2s12Enabled && catB2s12.band == "ok" && catB2s12.label.isNotBlank())) {
                    Text(catB2s12.label, color = Color(com.veplayer.app.vehicle.CatalystB2S12.accentArgb(catB2s12.band)), fontSize = 11.sp)
                }
                if (stftB2.showWarn || (prefs.stftB2Enabled && stftB2.band == "ok" && stftB2.label.isNotBlank())) {
                    Text(stftB2.label, color = Color(com.veplayer.app.vehicle.FuelTrimStftB2.accentArgb(stftB2.band)), fontSize = 11.sp)
                }
                if (ltftB2.showWarn || (prefs.ltftB2Enabled && ltftB2.band == "ok" && ltftB2.label.isNotBlank())) {
                    Text(ltftB2.label, color = Color(com.veplayer.app.vehicle.FuelTrimLtftB2.accentArgb(ltftB2.band)), fontSize = 11.sp)
                }
                if (catB1s13.showWarn || (prefs.catB1s13Enabled && catB1s13.band == "ok" && catB1s13.label.isNotBlank())) {
                    Text(catB1s13.label, color = Color(com.veplayer.app.vehicle.CatalystB1S13.accentArgb(catB1s13.band)), fontSize = 11.sp)
                }
                if (catB2s13.showWarn || (prefs.catB2s13Enabled && catB2s13.band == "ok" && catB2s13.label.isNotBlank())) {
                    Text(catB2s13.label, color = Color(com.veplayer.app.vehicle.CatalystB2S13.accentArgb(catB2s13.band)), fontSize = 11.sp)
                }
                if (dpfTrig.showWarn || (prefs.dpfTrigEnabled && dpfTrig.band == "ok" && dpfTrig.label.isNotBlank())) {
                    Text(dpfTrig.label, color = Color(com.veplayer.app.vehicle.DpfAftertreatment.accentArgb(dpfTrig.band)), fontSize = 11.sp)
                }
                if (thrG.showWarn || (prefs.thrGEnabled && thrG.band == "ok" && thrG.label.isNotBlank())) {
                    Text(thrG.label, color = Color(com.veplayer.app.vehicle.ThrottleG.accentArgb(thrG.band)), fontSize = 11.sp)
                }
                if (engFriction.showWarn || (prefs.engFrictionEnabled && engFriction.band == "ok" && engFriction.label.isNotBlank())) {
                    Text(engFriction.label, color = Color(com.veplayer.app.vehicle.EngineFrictionTorque.accentArgb(engFriction.band)), fontSize = 11.sp)
                }
                if (catB1s14.showWarn || (prefs.catB1s14Enabled && catB1s14.band == "ok" && catB1s14.label.isNotBlank())) {
                    Text(catB1s14.label, color = Color(com.veplayer.app.vehicle.CatalystB1S14.accentArgb(catB1s14.band)), fontSize = 11.sp)
                }
                if (catB2s14.showWarn || (prefs.catB2s14Enabled && catB2s14.band == "ok" && catB2s14.label.isNotBlank())) {
                    Text(catB2s14.label, color = Color(com.veplayer.app.vehicle.CatalystB2S14.accentArgb(catB2s14.band)), fontSize = 11.sp)
                }
                if (o2Lambda.showWarn || (prefs.o2LambdaEnabled && o2Lambda.band == "ok" && o2Lambda.label.isNotBlank())) {
                    Text(o2Lambda.label, color = Color(com.veplayer.app.vehicle.O2LambdaB1.accentArgb(o2Lambda.band)), fontSize = 11.sp)
                }
                if (pmB1.showWarn || (prefs.pmB1Enabled && pmB1.band == "ok" && pmB1.label.isNotBlank())) {
                    Text(pmB1.label, color = Color(com.veplayer.app.vehicle.PmSensorB1.accentArgb(pmB1.band)), fontSize = 11.sp)
                }
                if (pmB2.showWarn || (prefs.pmB2Enabled && pmB2.band == "ok" && pmB2.label.isNotBlank())) {
                    Text(pmB2.label, color = Color(com.veplayer.app.vehicle.PmSensorB2.accentArgb(pmB2.band)), fontSize = 11.sp)
                }
                if (egtB1s5.showWarn || (prefs.egtB1s5Enabled && egtB1s5.band == "ok" && egtB1s5.label.isNotBlank())) {
                    Text(egtB1s5.label, color = Color(com.veplayer.app.vehicle.EgtB1S5.accentArgb(egtB1s5.band)), fontSize = 11.sp)
                }
                if (egtB2s5.showWarn || (prefs.egtB2s5Enabled && egtB2s5.band == "ok" && egtB2s5.label.isNotBlank())) {
                    Text(egtB2s5.label, color = Color(com.veplayer.app.vehicle.EgtB2S5.accentArgb(egtB2s5.band)), fontSize = 11.sp)
                }
                if (o2LambdaB1s3.showWarn || (prefs.o2LambdaB1s3Enabled && o2LambdaB1s3.band == "ok" && o2LambdaB1s3.label.isNotBlank())) {
                    Text(o2LambdaB1s3.label, color = Color(com.veplayer.app.vehicle.O2LambdaB1S3.accentArgb(o2LambdaB1s3.band)), fontSize = 11.sp)
                }
                if (o2LambdaB2s3.showWarn || (prefs.o2LambdaB2s3Enabled && o2LambdaB2s3.band == "ok" && o2LambdaB2s3.label.isNotBlank())) {
                    Text(o2LambdaB2s3.label, color = Color(com.veplayer.app.vehicle.O2LambdaB2S3.accentArgb(o2LambdaB2s3.band)), fontSize = 11.sp)
                }
                if (noxReq.showWarn || (prefs.noxReqEnabled && noxReq.band == "ok" && noxReq.label.isNotBlank())) {
                    Text(noxReq.label, color = Color(com.veplayer.app.vehicle.NoxReagentQuality.accentArgb(noxReq.band)), fontSize = 11.sp)
                }
                if (egtB1s6.showWarn || (prefs.egtB1s6Enabled && egtB1s6.band == "ok" && egtB1s6.label.isNotBlank())) {
                    Text(egtB1s6.label, color = Color(com.veplayer.app.vehicle.EgtB1S6.accentArgb(egtB1s6.band)), fontSize = 11.sp)
                }
                if (egtB2s6.showWarn || (prefs.egtB2s6Enabled && egtB2s6.band == "ok" && egtB2s6.label.isNotBlank())) {
                    Text(egtB2s6.label, color = Color(com.veplayer.app.vehicle.EgtB2S6.accentArgb(egtB2s6.band)), fontSize = 11.sp)
                }
                if (o2LambdaB1s4.showWarn || (prefs.o2LambdaB1s4Enabled && o2LambdaB1s4.band == "ok" && o2LambdaB1s4.label.isNotBlank())) {
                    Text(o2LambdaB1s4.label, color = Color(com.veplayer.app.vehicle.O2LambdaB1S4.accentArgb(o2LambdaB1s4.band)), fontSize = 11.sp)
                }
                if (o2LambdaB2s4.showWarn || (prefs.o2LambdaB2s4Enabled && o2LambdaB2s4.band == "ok" && o2LambdaB2s4.label.isNotBlank())) {
                    Text(o2LambdaB2s4.label, color = Color(com.veplayer.app.vehicle.O2LambdaB2S4.accentArgb(o2LambdaB2s4.band)), fontSize = 11.sp)
                }
                if (defFluid.showWarn || (prefs.defFluidEnabled && defFluid.band == "ok" && defFluid.label.isNotBlank())) {
                    Text(defFluid.label, color = Color(com.veplayer.app.vehicle.DefFluid.accentArgb(defFluid.band)), fontSize = 11.sp)
                }
                if (egtB1s7.showWarn || (prefs.egtB1s7Enabled && egtB1s7.band == "ok" && egtB1s7.label.isNotBlank())) {
                    Text(egtB1s7.label, color = Color(com.veplayer.app.vehicle.EgtB1S7.accentArgb(egtB1s7.band)), fontSize = 11.sp)
                }
                if (egtB2s7.showWarn || (prefs.egtB2s7Enabled && egtB2s7.band == "ok" && egtB2s7.label.isNotBlank())) {
                    Text(egtB2s7.label, color = Color(com.veplayer.app.vehicle.EgtB2S7.accentArgb(egtB2s7.band)), fontSize = 11.sp)
                }
                if (egtB1s8.showWarn || (prefs.egtB1s8Enabled && egtB1s8.band == "ok" && egtB1s8.label.isNotBlank())) {
                    Text(egtB1s8.label, color = Color(com.veplayer.app.vehicle.EgtB1S8.accentArgb(egtB1s8.band)), fontSize = 11.sp)
                }
                if (egtB2s8.showWarn || (prefs.egtB2s8Enabled && egtB2s8.band == "ok" && egtB2s8.label.isNotBlank())) {
                    Text(egtB2s8.label, color = Color(com.veplayer.app.vehicle.EgtB2S8.accentArgb(egtB2s8.band)), fontSize = 11.sp)
                }
                if (o2ConcB1s3.showWarn || (prefs.o2ConcB1s3Enabled && o2ConcB1s3.band == "ok" && o2ConcB1s3.label.isNotBlank())) {
                    Text(o2ConcB1s3.label, color = Color(com.veplayer.app.vehicle.O2ConcB1S3.accentArgb(o2ConcB1s3.band)), fontSize = 11.sp)
                }
                if (o2ConcB1s4.showWarn || (prefs.o2ConcB1s4Enabled && o2ConcB1s4.band == "ok" && o2ConcB1s4.label.isNotBlank())) {
                    Text(o2ConcB1s4.label, color = Color(com.veplayer.app.vehicle.O2ConcB1S4.accentArgb(o2ConcB1s4.band)), fontSize = 11.sp)
                }
                if (o2ConcB2s3.showWarn || (prefs.o2ConcB2s3Enabled && o2ConcB2s3.band == "ok" && o2ConcB2s3.label.isNotBlank())) {
                    Text(o2ConcB2s3.label, color = Color(com.veplayer.app.vehicle.O2ConcB2S3.accentArgb(o2ConcB2s3.band)), fontSize = 11.sp)
                }
                if (o2ConcB2s4.showWarn || (prefs.o2ConcB2s4Enabled && o2ConcB2s4.band == "ok" && o2ConcB2s4.label.isNotBlank())) {
                    Text(o2ConcB2s4.label, color = Color(com.veplayer.app.vehicle.O2ConcB2S4.accentArgb(o2ConcB2s4.band)), fontSize = 11.sp)
                }
                if (defDose.showWarn || (prefs.defDoseEnabled && defDose.band == "ok" && defDose.label.isNotBlank())) {
                    Text(defDose.label, color = Color(com.veplayer.app.vehicle.DefDosingCmd.accentArgb(defDose.band)), fontSize = 11.sp)
                }
                if (noxCorrB1s1.showWarn || (prefs.noxCorrB1s1Enabled && noxCorrB1s1.band == "ok" && noxCorrB1s1.label.isNotBlank())) {
                    Text(noxCorrB1s1.label, color = Color(com.veplayer.app.vehicle.NoxCorrectedB1S1.accentArgb(noxCorrB1s1.band)), fontSize = 11.sp)
                }
                if (noxCorrB1s2.showWarn || (prefs.noxCorrB1s2Enabled && noxCorrB1s2.band == "ok" && noxCorrB1s2.label.isNotBlank())) {
                    Text(noxCorrB1s2.label, color = Color(com.veplayer.app.vehicle.NoxCorrectedB1S2.accentArgb(noxCorrB1s2.band)), fontSize = 11.sp)
                }
                if (noxCorrB2s1.showWarn || (prefs.noxCorrB2s1Enabled && noxCorrB2s1.band == "ok" && noxCorrB2s1.label.isNotBlank())) {
                    Text(noxCorrB2s1.label, color = Color(com.veplayer.app.vehicle.NoxCorrectedB2S1.accentArgb(noxCorrB2s1.band)), fontSize = 11.sp)
                }
                if (noxCorrB2s2.showWarn || (prefs.noxCorrB2s2Enabled && noxCorrB2s2.band == "ok" && noxCorrB2s2.label.isNotBlank())) {
                    Text(noxCorrB2s2.label, color = Color(com.veplayer.app.vehicle.NoxCorrectedB2S2.accentArgb(noxCorrB2s2.band)), fontSize = 11.sp)
                }
                if (noxConcS3.showWarn || (prefs.noxConcS3Enabled && noxConcS3.band == "ok" && noxConcS3.label.isNotBlank())) {
                    Text(noxConcS3.label, color = Color(com.veplayer.app.vehicle.NoxConcS3.accentArgb(noxConcS3.band)), fontSize = 11.sp)
                }
                if (noxConcS4.showWarn || (prefs.noxConcS4Enabled && noxConcS4.band == "ok" && noxConcS4.label.isNotBlank())) {
                    Text(noxConcS4.label, color = Color(com.veplayer.app.vehicle.NoxConcS4.accentArgb(noxConcS4.band)), fontSize = 11.sp)
                }
                if (noxCorrS3.showWarn || (prefs.noxCorrS3Enabled && noxCorrS3.band == "ok" && noxCorrS3.label.isNotBlank())) {
                    Text(noxCorrS3.label, color = Color(com.veplayer.app.vehicle.NoxCorrectedS3.accentArgb(noxCorrS3.band)), fontSize = 11.sp)
                }
                if (noxCorrS4.showWarn || (prefs.noxCorrS4Enabled && noxCorrS4.band == "ok" && noxCorrS4.label.isNotBlank())) {
                    Text(noxCorrS4.label, color = Color(com.veplayer.app.vehicle.NoxCorrectedS4.accentArgb(noxCorrS4.band)), fontSize = 11.sp)
                }
                if (cylFuel.showWarn || (prefs.cylFuelEnabled && cylFuel.band == "ok" && cylFuel.label.isNotBlank())) {
                    Text(cylFuel.label, color = Color(com.veplayer.app.vehicle.CylinderFuelRate.accentArgb(cylFuel.band)), fontSize = 11.sp)
                }
                if (evapSysVapor.showWarn || (prefs.evapSysVaporEnabled && evapSysVapor.band == "ok" && evapSysVapor.label.isNotBlank())) {
                    Text(evapSysVapor.label, color = Color(com.veplayer.app.vehicle.EvapSysVapor.accentArgb(evapSysVapor.band)), fontSize = 11.sp)
                }
                if (transGear.showWarn || (prefs.transGearEnabled && transGear.band == "ok" && transGear.label.isNotBlank())) {
                    Text(transGear.label, color = Color(com.veplayer.app.vehicle.TransGearRatio.accentArgb(transGear.band)), fontSize = 11.sp)
                }
                if (obdOdo.showWarn || (prefs.obdOdoEnabled && obdOdo.band == "ok" && obdOdo.label.isNotBlank())) {
                    Text(obdOdo.label, color = Color(com.veplayer.app.vehicle.ObdOdometer.accentArgb(obdOdo.band)), fontSize = 11.sp)
                }
                if (absDisable.showWarn || (prefs.absDisableEnabled && absDisable.band == "ok" && absDisable.label.isNotBlank())) {
                    Text(absDisable.label, color = Color(com.veplayer.app.vehicle.AbsDisable.accentArgb(absDisable.band)), fontSize = 11.sp)
                }
                if (fuelPressA.showWarn || (prefs.fuelPressAEnabled && fuelPressA.band == "ok" && fuelPressA.label.isNotBlank())) {
                    Text(fuelPressA.label, color = Color(com.veplayer.app.vehicle.FuelPressA.accentArgb(fuelPressA.band)), fontSize = 11.sp)
                }
                if (fuelPressB.showWarn || (prefs.fuelPressBEnabled && fuelPressB.band == "ok" && fuelPressB.label.isNotBlank())) {
                    Text(fuelPressB.label, color = Color(com.veplayer.app.vehicle.FuelPressB.accentArgb(fuelPressB.band)), fontSize = 11.sp)
                }
                if (reflashDist.showWarn || (prefs.reflashDistEnabled && reflashDist.band == "ok" && reflashDist.label.isNotBlank())) {
                    Text(reflashDist.label, color = Color(com.veplayer.app.vehicle.ReflashDistance.accentArgb(reflashDist.band)), fontSize = 11.sp)
                }
                if (fuelLvlA.showWarn || (prefs.fuelLvlAEnabled && fuelLvlA.band == "ok" && fuelLvlA.label.isNotBlank())) {
                    Text(fuelLvlA.label, color = Color(com.veplayer.app.vehicle.FuelLevelInputA.accentArgb(fuelLvlA.band)), fontSize = 11.sp)
                }
                if (fuelLvlB.showWarn || (prefs.fuelLvlBEnabled && fuelLvlB.band == "ok" && fuelLvlB.label.isNotBlank())) {
                    Text(fuelLvlB.label, color = Color(com.veplayer.app.vehicle.FuelLevelInputB.accentArgb(fuelLvlB.band)), fontSize = 11.sp)
                }
                if (epcsTime.showWarn || (prefs.epcsTimeEnabled && epcsTime.band == "ok" && epcsTime.label.isNotBlank())) {
                    Text(epcsTime.label, color = Color(com.veplayer.app.vehicle.EpcsDiagTime.accentArgb(epcsTime.band)), fontSize = 11.sp)
                }
                if (epcsCount.showWarn || (prefs.epcsCountEnabled && epcsCount.band == "ok" && epcsCount.label.isNotBlank())) {
                    Text(epcsCount.label, color = Color(com.veplayer.app.vehicle.EpcsDiagCount.accentArgb(epcsCount.band)), fontSize = 11.sp)
                }
                if (noxPcdLamp.showWarn || (prefs.noxPcdLampEnabled && noxPcdLamp.band == "ok" && noxPcdLamp.label.isNotBlank())) {
                    Text(noxPcdLamp.label, color = Color(com.veplayer.app.vehicle.NoxPcdLamp.accentArgb(noxPcdLamp.band)), fontSize = 11.sp)
                }
                if (particulateInduceWarn.showWarn || (prefs.particulateInduceWarnEnabled && particulateInduceWarn.band == "ok" && particulateInduceWarn.label.isNotBlank())) {
                    Text(particulateInduceWarn.label, color = Color(com.veplayer.app.vehicle.ParticulateInduceWarn.accentArgb(particulateInduceWarn.band)), fontSize = 11.sp)
                }
                if (particulateInduceAlert.showWarn || (prefs.particulateInduceAlertEnabled && particulateInduceAlert.band == "ok" && particulateInduceAlert.label.isNotBlank())) {
                    Text(particulateInduceAlert.label, color = Color(com.veplayer.app.vehicle.ParticulateInduceAlert.accentArgb(particulateInduceAlert.band)), fontSize = 11.sp)
                }
                if (dpfRemoval.showWarn || (prefs.dpfRemovalEnabled && dpfRemoval.band == "ok" && dpfRemoval.label.isNotBlank())) {
                    Text(dpfRemoval.label, color = Color(com.veplayer.app.vehicle.DpfRemovalCounter.accentArgb(dpfRemoval.band)), fontSize = 11.sp)
                }
                if (reagentFail.showWarn || (prefs.reagentFailEnabled && reagentFail.band == "ok" && reagentFail.label.isNotBlank())) {
                    Text(reagentFail.label, color = Color(com.veplayer.app.vehicle.ReagentInjectionFailCounter.accentArgb(reagentFail.band)), fontSize = 11.sp)
                }
                if (particulateMalf.showWarn || (prefs.particulateMalfEnabled && particulateMalf.band == "ok" && particulateMalf.label.isNotBlank())) {
                    Text(particulateMalf.label, color = Color(com.veplayer.app.vehicle.ParticulateMonitorMalfunctionCounter.accentArgb(particulateMalf.band)), fontSize = 11.sp)
                }
                if (engineFuelRateGps.showWarn || (prefs.engineFuelRateGpsEnabled && engineFuelRateGps.band == "ok" && engineFuelRateGps.label.isNotBlank())) {
                    Text(engineFuelRateGps.label, color = Color(com.veplayer.app.vehicle.EngineFuelRateGps.accentArgb(engineFuelRateGps.band)), fontSize = 11.sp)
                }
                if (exhaustFlow.showWarn || (prefs.exhaustFlowEnabled && exhaustFlow.band == "ok" && exhaustFlow.label.isNotBlank())) {
                    Text(exhaustFlow.label, color = Color(com.veplayer.app.vehicle.EngineExhaustFlow.accentArgb(exhaustFlow.band)), fontSize = 11.sp)
                }
                if (fuelSysUse1.showWarn || (prefs.fuelSysUse1Enabled && fuelSysUse1.band == "ok" && fuelSysUse1.label.isNotBlank())) {
                    Text(fuelSysUse1.label, color = Color(com.veplayer.app.vehicle.FuelSysUsePct1.accentArgb(fuelSysUse1.band)), fontSize = 11.sp)
                }
                if (fuelSysUse2.showWarn || (prefs.fuelSysUse2Enabled && fuelSysUse2.band == "ok" && fuelSysUse2.label.isNotBlank())) {
                    Text(fuelSysUse2.label, color = Color(com.veplayer.app.vehicle.FuelSysUsePct2.accentArgb(fuelSysUse2.band)), fontSize = 11.sp)
                }
                if (fuelSysUse3.showWarn || (prefs.fuelSysUse3Enabled && fuelSysUse3.band == "ok" && fuelSysUse3.label.isNotBlank())) {
                    Text(fuelSysUse3.label, color = Color(com.veplayer.app.vehicle.FuelSysUsePct3.accentArgb(fuelSysUse3.band)), fontSize = 11.sp)
                }
                if (wwhContMi.showWarn || (prefs.wwhContMiEnabled && wwhContMi.band == "ok" && wwhContMi.label.isNotBlank())) {
                    Text(wwhContMi.label, color = Color(com.veplayer.app.vehicle.WwhObdContinuousMi.accentArgb(wwhContMi.band)), fontSize = 11.sp)
                }
                if (wwhEcuB1.showWarn || (prefs.wwhEcuB1Enabled && wwhEcuB1.band == "ok" && wwhEcuB1.label.isNotBlank())) {
                    Text(wwhEcuB1.label, color = Color(com.veplayer.app.vehicle.WwhObdEcuB1Hours.accentArgb(wwhEcuB1.band)), fontSize = 11.sp)
                }
                if (wwhCumMi.showWarn || (prefs.wwhCumMiEnabled && wwhCumMi.band == "ok" && wwhCumMi.label.isNotBlank())) {
                    Text(wwhCumMi.label, color = Color(com.veplayer.app.vehicle.WwhObdCumulativeMi.accentArgb(wwhCumMi.band)), fontSize = 11.sp)
                }
                if (fuelSysCtl.showWarn || (prefs.fuelSysCtlEnabled && fuelSysCtl.band == "ok" && fuelSysCtl.label.isNotBlank())) {
                    Text(fuelSysCtl.label, color = Color(com.veplayer.app.vehicle.FuelSysCtlClosed.accentArgb(fuelSysCtl.band)), fontSize = 11.sp)
                }
                if (hevVolt.showWarn || (prefs.hevVoltEnabled && hevVolt.band == "ok" && hevVolt.label.isNotBlank())) {
                    Text(hevVolt.label, color = Color(com.veplayer.app.vehicle.HybridEvBattVoltage.accentArgb(hevVolt.band)), fontSize = 11.sp)
                }
                if (noxWarn.showWarn || (prefs.noxWarnEnabled && noxWarn.band == "ok" && noxWarn.label.isNotBlank())) {
                    Text(noxWarn.label, color = Color(com.veplayer.app.vehicle.NoxWarnActive.accentArgb(noxWarn.band)), fontSize = 11.sp)
                }
                if (noxIndL1.showWarn || (prefs.noxIndL1Enabled && noxIndL1.band == "ok" && noxIndL1.label.isNotBlank())) {
                    Text(noxIndL1.label, color = Color(com.veplayer.app.vehicle.NoxInduceLevel1.accentArgb(noxIndL1.band)), fontSize = 11.sp)
                }
                if (noxIndL2.showWarn || (prefs.noxIndL2Enabled && noxIndL2.band == "ok" && noxIndL2.label.isNotBlank())) {
                    Text(noxIndL2.label, color = Color(com.veplayer.app.vehicle.NoxInduceLevel2.accentArgb(noxIndL2.band)), fontSize = 11.sp)
                }
                if (noxEgr.showWarn || (prefs.noxEgrEnabled && noxEgr.band == "ok" && noxEgr.label.isNotBlank())) {
                    Text(noxEgr.label, color = Color(com.veplayer.app.vehicle.NoxEgrCounter.accentArgb(noxEgr.band)), fontSize = 11.sp)
                }
                if (noxMal.showWarn || (prefs.noxMalEnabled && noxMal.band == "ok" && noxMal.label.isNotBlank())) {
                    Text(noxMal.label, color = Color(com.veplayer.app.vehicle.NoxMonitorMalfunction.accentArgb(noxMal.band)), fontSize = 11.sp)
                }
                if (hvSoh.showWarn || (prefs.hvSohEnabled && hvSoh.band == "ok" && hvSoh.label.isNotBlank())) {
                    Text(hvSoh.label, color = Color(HvBattSoh.accentArgb(hvSoh.band)), fontSize = 11.sp)
                }
                if (hvessTemp.showWarn || (prefs.hvessTempEnabled && hvessTemp.band == "ok" && hvessTemp.label.isNotBlank())) {
                    Text(hvessTemp.label, color = Color(HvessTemp.accentArgb(hvessTemp.band)), fontSize = 11.sp)
                }
                if (hvessCur.showWarn || (prefs.hvessCurEnabled && hvessCur.band == "ok" && hvessCur.label.isNotBlank())) {
                    Text(hvessCur.label, color = Color(HvessCurrent.accentArgb(hvessCur.band)), fontSize = 11.sp)
                }
                if (hvessVolt.showWarn || (prefs.hvessVoltEnabled && hvessVolt.band == "ok" && hvessVolt.label.isNotBlank())) {
                    Text(hvessVolt.label, color = Color(HvessPackVoltage.accentArgb(hvessVolt.band)), fontSize = 11.sp)
                }
                if (hvCellMax.showWarn || (prefs.hvCellMaxEnabled && hvCellMax.band == "ok" && hvCellMax.label.isNotBlank())) {
                    Text(hvCellMax.label, color = Color(HvCellMaxTemp.accentArgb(hvCellMax.band)), fontSize = 11.sp)
                }
                if (hvBal.showWarn || (prefs.hvBalEnabled && hvBal.band == "ok" && hvBal.label.isNotBlank())) {
                    Text(hvBal.label, color = Color(HvBalHours.accentArgb(hvBal.band)), fontSize = 11.sp)
                }
                if (hvCellMinV.showWarn || (prefs.hvCellMinVEnabled && hvCellMinV.band == "ok" && hvCellMinV.label.isNotBlank())) {
                    Text(hvCellMinV.label, color = Color(HvCellMinVolt.accentArgb(hvCellMinV.band)), fontSize = 11.sp)
                }
                if (hvCellMaxV.showWarn || (prefs.hvCellMaxVEnabled && hvCellMaxV.band == "ok" && hvCellMaxV.label.isNotBlank())) {
                    Text(hvCellMaxV.label, color = Color(HvCellMaxVolt.accentArgb(hvCellMaxV.band)), fontSize = 11.sp)
                }
                if (hvPwr.showWarn || (prefs.hvPwrEnabled && hvPwr.band == "ok" && hvPwr.label.isNotBlank())) {
                    Text(hvPwr.label, color = Color(HvPwrAvail.accentArgb(hvPwr.band)), fontSize = 11.sp)
                }
                if (hvChg.showWarn || (prefs.hvChgEnabled && hvChg.band == "ok" && hvChg.label.isNotBlank())) {
                    Text(hvChg.label, color = Color(HvChgLimit.accentArgb(hvChg.band)), fontSize = 11.sp)
                }
                if (hvCellMinT.showWarn || (prefs.hvCellMinTEnabled && hvCellMinT.band == "ok" && hvCellMinT.label.isNotBlank())) {
                    Text(hvCellMinT.label, color = Color(HvCellMinTemp.accentArgb(hvCellMinT.band)), fontSize = 11.sp)
                }
                if (hvDis.showWarn || (prefs.hvDisEnabled && hvDis.band == "ok" && hvDis.label.isNotBlank())) {
                    Text(hvDis.label, color = Color(HvDisLimit.accentArgb(hvDis.band)), fontSize = 11.sp)
                }
                if (hvEnrgIn.showWarn || (prefs.hvEnrgInEnabled && hvEnrgIn.band == "ok" && hvEnrgIn.label.isNotBlank())) {
                    Text(hvEnrgIn.label, color = Color(HvEnrgIn.accentArgb(hvEnrgIn.band)), fontSize = 11.sp)
                }
                if (hvEnrgOut.showWarn || (prefs.hvEnrgOutEnabled && hvEnrgOut.band == "ok" && hvEnrgOut.label.isNotBlank())) {
                    Text(hvEnrgOut.label, color = Color(HvEnrgOut.accentArgb(hvEnrgOut.band)), fontSize = 11.sp)
                }
                if (hvEnrgTput.showWarn || (prefs.hvEnrgTputEnabled && hvEnrgTput.band == "ok" && hvEnrgTput.label.isNotBlank())) {
                    Text(hvEnrgTput.label, color = Color(HvEnrgTput.accentArgb(hvEnrgTput.band)), fontSize = 11.sp)
                }
                if (hvAcr.showWarn || (prefs.hvAcrEnabled && hvAcr.band == "ok" && hvAcr.label.isNotBlank())) {
                    Text(hvAcr.label, color = Color(HvAcr.accentArgb(hvAcr.band)), fontSize = 11.sp)
                }
                if (hvessSoh.showWarn || (prefs.hvessSohEnabled && hvessSoh.band == "ok" && hvessSoh.label.isNotBlank())) {
                    Text(hvessSoh.label, color = Color(HvessSoh.accentArgb(hvessSoh.band)), fontSize = 11.sp)
                }
                if (hvMinSoc.showWarn || (prefs.hvMinSocEnabled && hvMinSoc.band == "ok" && hvMinSoc.label.isNotBlank())) {
                    Text(hvMinSoc.label, color = Color(HvMinSoc.accentArgb(hvMinSoc.band)), fontSize = 11.sp)
                }
                if (hvMaxSoc.showWarn || (prefs.hvMaxSocEnabled && hvMaxSoc.band == "ok" && hvMaxSoc.label.isNotBlank())) {
                    Text(hvMaxSoc.label, color = Color(HvMaxSoc.accentArgb(hvMaxSoc.band)), fontSize = 11.sp)
                }
                if (hvDcap.showWarn || (prefs.hvDcapEnabled && hvDcap.band == "ok" && hvDcap.label.isNotBlank())) {
                    Text(hvDcap.label, color = Color(HvDcap.accentArgb(hvDcap.band)), fontSize = 11.sp)
                }
                if (hvSoce.showWarn || (prefs.hvSoceEnabled && hvSoce.band == "ok" && hvSoce.label.isNotBlank())) {
                    Text(hvSoce.label, color = Color(HvSoce.accentArgb(hvSoce.band)), fontSize = 11.sp)
                }
                if (essCap.showWarn || (prefs.essCapEnabled && essCap.band == "ok" && essCap.label.isNotBlank())) {
                    Text(essCap.label, color = Color(EssCap.accentArgb(essCap.band)), fontSize = 11.sp)
                }
                if (bcapReady.showWarn || (prefs.bcapReadyEnabled && bcapReady.band == "ok" && bcapReady.label.isNotBlank())) {
                    Text(bcapReady.label, color = Color(BcapReady.accentArgb(bcapReady.band)), fontSize = 11.sp)
                }
                if (essRsrv.showWarn || (prefs.essRsrvEnabled && essRsrv.band == "ok" && essRsrv.label.isNotBlank())) {
                    Text(essRsrv.label, color = Color(EssRsrv.accentArgb(essRsrv.band)), fontSize = 11.sp)
                }
                if (essChgLim.showWarn || (prefs.essChgLimEnabled && essChgLim.band == "ok" && essChgLim.label.isNotBlank())) {
                    Text(essChgLim.label, color = Color(EssChgLim.accentArgb(essChgLim.band)), fontSize = 11.sp)
                }
                if (essChgAct.showWarn || (prefs.essChgActEnabled && essChgAct.band == "ok" && essChgAct.label.isNotBlank())) {
                    Text(essChgAct.label, color = Color(EssChgAct.accentArgb(essChgAct.band)), fontSize = 11.sp)
                }
                if (hvEnerRate.showWarn || (prefs.hvEnerRateEnabled && hvEnerRate.band == "ok" && hvEnerRate.label.isNotBlank())) {
                    Text(hvEnerRate.label, color = Color(HvEnerRate.accentArgb(hvEnerRate.band)), fontSize = 11.sp)
                }
                if (hvCurrRate.showWarn || (prefs.hvCurrRateEnabled && hvCurrRate.band == "ok" && hvCurrRate.label.isNotBlank())) {
                    Text(hvCurrRate.label, color = Color(HvCurrRate.accentArgb(hvCurrRate.band)), fontSize = 11.sp)
                }
                if (emRpm.showWarn || (prefs.emRpmEnabled && emRpm.band == "ok" && emRpm.label.isNotBlank())) {
                    Text(emRpm.label, color = Color(EmRpm.accentArgb(emRpm.band)), fontSize = 11.sp)
                }
                if (emTq.showWarn || (prefs.emTqEnabled && emTq.band == "ok" && emTq.label.isNotBlank())) {
                    Text(emTq.label, color = Color(EmTq.accentArgb(emTq.band)), fontSize = 11.sp)
                }
                if (fcVolt.showWarn || (prefs.fcVoltEnabled && fcVolt.band == "ok" && fcVolt.label.isNotBlank())) {
                    Text(fcVolt.label, color = Color(FcVolt.accentArgb(fcVolt.band)), fontSize = 11.sp)
                }
                if (fcFuelRate.showWarn || (prefs.fcFuelRateEnabled && fcFuelRate.band == "ok" && fcFuelRate.label.isNotBlank())) {
                    Text(fcFuelRate.label, color = Color(FcFuelRate.accentArgb(fcFuelRate.band)), fontSize = 11.sp)
                }
                if (psTrips.showWarn || (prefs.psTripsEnabled && psTrips.band == "ok" && psTrips.label.isNotBlank())) {
                    Text(psTrips.label, color = Color(PsTrips.accentArgb(psTrips.band)), fontSize = 11.sp)
                }
                if (hevMode.showWarn || (prefs.hevModeEnabled && hevMode.band == "ok" && hevMode.label.isNotBlank())) {
                    Text(hevMode.label, color = Color(HevMode.accentArgb(hevMode.band)), fontSize = 11.sp)
                }
                if (hevBattCurr.showWarn || (prefs.hevBattCurrEnabled && hevBattCurr.band == "ok" && hevBattCurr.label.isNotBlank())) {
                    Text(hevBattCurr.label, color = Color(HevBattCurr.accentArgb(hevBattCurr.band)), fontSize = 11.sp)
                }
                if (vSet.showWarn || (prefs.vSetEnabled && vSet.band == "ok" && vSet.label.isNotBlank())) {
                    Text(vSet.label, color = Color(VSet.accentArgb(vSet.band)), fontSize = 11.sp)
                }
                if (milDist.showWarn || (prefs.milDistEnabled && milDist.milOn && milDist.label.isNotBlank())) {
                    Text(
                        milDist.label,
                        color = Color(com.veplayer.app.vehicle.MilDistance.accentArgb(milDist.band)),
                        fontSize = 11.sp,
                    )
                }
                if (distClear.showWarn || (prefs.distClearEnabled && distClear.faultActive && distClear.label.isNotBlank())) {
                    Text(
                        distClear.label,
                        color = Color(com.veplayer.app.vehicle.DistSinceClear.accentArgb(distClear.band)),
                        fontSize = 11.sp,
                    )
                }
                if (tpmsHud.showWarn || (prefs.tpmsHudEnabled && tpmsHud.band == "ok")) {
                    Text(
                        tpmsHud.label.ifBlank { "TPMS" },
                        color = Color(com.veplayer.app.vehicle.TpmsHud.accentArgb(tpmsHud.band)),
                        fontSize = 11.sp,
                    )
                }
                if (battV.showWarn || (prefs.battVoltEnabled && battV.band == "ok")) {
                    Text(
                        "Bat · ${battV.label}",
                        color = Color(com.veplayer.app.vehicle.BatteryVoltage.accentArgb(battV.band)),
                        fontSize = 11.sp,
                    )
                }
                val pendingMsg by com.veplayer.app.fleet.MessageReplyBus.pending.collectAsState()
                pendingMsg?.let { msg ->
                    if (prefs.messageReplyEnabled && msg.status == "pending") {
                        Text(
                            com.veplayer.app.fleet.MessageReplyBus.label(msg),
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                        )
                    }
                }
                val inboxLast by com.veplayer.app.fleet.FleetInbox.last.collectAsState()
                inboxLast?.let { item ->
                    Text(
                        "Flota · ${item.text.take(48)}",
                        color = Mute,
                        fontSize = 11.sp,
                    )
                }
            }
            if (brand.hasLogo) {
                BrandLogo(
                    modifier = Modifier.padding(bottom = 8.dp),
                    height = 40.dp,
                )
            }
            if (prefs.speedHudEnabled) {
                val zone by com.veplayer.app.vehicle.SpeedZoneBus.zone.collectAsState()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    SpeedLimitBadge(
                        limitKmh = hud.limitKmh,
                        band = hud.band,
                    )
                    if (prefs.geofenceSpeedEnabled && zone != null) {
                        Text(
                            zone!!.name.take(18),
                            color = Color(0xFFF59E0B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            if (prefs.panicEnabled) {
                val sosColor =
                    when {
                        panic.active -> Color(0xFFE11D48)
                        holdProgress > 0f -> Color(0xFFF59E0B)
                        else -> Color(0xFF7F1D1D)
                    }
                Box(
                    modifier =
                        Modifier
                            .padding(bottom = 8.dp, start = 6.dp)
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(sosColor)
                            .pointerInput(panic.active) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    holdJob?.cancel()
                                    holdProgress = 0f
                                    PanicBus.setHolding(true, 0f)
                                    holdJob =
                                        scope.launch {
                                            val total = 1200L
                                            val step = 50L
                                            var t = 0L
                                            while (t < total) {
                                                delay(step)
                                                t += step
                                                holdProgress = t / total.toFloat()
                                                PanicBus.setHolding(true, holdProgress)
                                            }
                                            holdProgress = 1f
                                        }
                                    waitForUpOrCancellation()
                                    val fired = holdProgress >= 0.99f
                                    holdJob?.cancel()
                                    holdJob = null
                                    holdProgress = 0f
                                    PanicBus.setHolding(false, 0f)
                                    if (fired && !panic.active) {
                                        scope.launch { PanicBus.trigger(prefs, fleet, context) }
                                    }
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (panic.active) "SOS!" else "SOS",
                        color = Mist,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
        if (panic.active) {
            Text(
                "SOS activo · flota notificada" +
                    if (!panic.clipUrl.isNullOrBlank()) " · clip" else "",
                color = Color(0xFFE11D48),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (prefs.fuelHudEnabled && (vehicle.fuelPct != null || vehicle.batterySocPct != null || vehicle.rangeKm != null)) {
            Text(
                FuelRangeHud.labelLine(fuelHud),
                color = Color(FuelRangeHud.accentArgb(fuelHud.band)),
                fontSize = 12.sp,
            )
        } else {
            vehicle.batterySocPct?.let { soc ->
                Text(
                    "SOC ${soc.toInt()}% · rango ${vehicle.rangeKm?.toInt() ?: "—"} km",
                    color = Mute,
                    fontSize = 12.sp,
                )
            }
        }
        vehicle.rpm?.let { rpm ->
            Text("RPM ${rpm.toInt()} · coolant ${vehicle.coolantC?.toInt() ?: "—"}°C", color = Mute, fontSize = 12.sp)
        }
        Text(
            buildString {
                if (vehicle.absActive) append("ABS · ")
                if (dtc.label.isNotBlank()) {
                    append(dtc.label)
                    append(" · ")
                }
                if (prefs.tpmsHudEnabled && tpmsHud.detail.isNotBlank()) {
                    append(tpmsHud.detail)
                    append(" · ")
                } else {
                    vehicle.tpmsFlPsi?.let {
                        append("TPMS ${it.toInt()}")
                        if (vehicle.tpmsLow) append("!")
                        append(" · ")
                    }
                }
                if (prefs.hvacPanelEnabled && hvac.label.isNotBlank()) {
                    append(hvac.label)
                } else {
                    vehicle.hvacCabinC?.let {
                        append("HVAC ${it.toInt()}°")
                        if (vehicle.hvacAcOn) append(" AC")
                    }
                }
            }.ifBlank { "—" },
            color =
                when {
                    dtc.mil -> Color(0xFFF59E0B)
                    tpmsHud.showWarn ->
                        Color(com.veplayer.app.vehicle.TpmsHud.accentArgb(tpmsHud.band))
                    prefs.hvacPanelEnabled && hvac.showPanel ->
                        Color(com.veplayer.app.vehicle.HvacClimate.accentArgb(hvac.band))
                    else -> Mute
                },
            fontSize = 12.sp,
        )
        val counts =
            surround.actors.groupingBy { it.kind }.eachCount()
        Text(
            buildString {
                if (prefs.speedHudEnabled) {
                    append("Límite ${hud.limitKmh}")
                    if (hud.band == "near") append(" · cerca")
                    if (hud.showWarn) append(" · exceso")
                } else {
                    append("HUD off")
                }
                if (surround.actors.isNotEmpty()) {
                    append(" · ")
                    append(counts[ActorKind.PERSON] ?: 0)
                    append(" personas · ")
                    append((counts[ActorKind.MOTORCYCLE] ?: 0) + (counts[ActorKind.BICYCLE] ?: 0))
                    append(" motos/bici · ")
                    append(
                        (counts[ActorKind.CAR] ?: 0) +
                            (counts[ActorKind.TRUCK] ?: 0) +
                            (counts[ActorKind.BUS] ?: 0),
                    )
                    append(" vehículos")
                }
            },
            color = if (hud.showWarn) speedColor else Mute,
            fontSize = 13.sp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0A0A0A)),
        ) {
            RoadSceneCanvas(
                actors = surround.actors,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Card)
                .padding(14.dp),
        ) {
            val srcLabel =
                when (media.source) {
                    MediaSource.RADIO -> "RADIO"
                    MediaSource.FM -> "FM"
                    MediaSource.SPOTIFY -> "SPOTIFY"
                    MediaSource.PHONE -> "PHONE"
                    MediaSource.NONE -> "MEDIA"
                }
            Text(media.title, color = Mist, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                buildString {
                    append(media.artist)
                    if (media.subtitle.isNotBlank()) append(" · ${media.subtitle}")
                    append(" · $srcLabel")
                },
                color = Mute,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (media.progress >= 0f) {
                LinearProgressIndicator(
                    progress = { media.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Mist,
                    trackColor = Color(0xFF333333),
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Mist,
                    trackColor = Color(0xFF333333),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { VeMediaHub.skipPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Mist)
                }
                IconButton(onClick = { VeMediaHub.togglePlayPause() }) {
                    Icon(
                        if (media.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Mist,
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = { VeMediaHub.skipNext() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Mist)
                }
            }
        }
    }
}

@Composable
private fun TurnChip(label: String, on: Boolean) {
    Text(
        label,
        color = if (on) Color(0xFFFFC107) else Mute,
        fontSize = 12.sp,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
    )
}

@Composable
private fun RoadSceneCanvas(
    actors: List<SurroundActor>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRect(Road, topLeft = Offset(w * 0.18f, 0f), size = Size(w * 0.64f, h))
        var y = 20f
        while (y < h) {
            drawRoundRect(
                color = Lane,
                topLeft = Offset(w * 0.49f, y),
                size = Size(w * 0.02f, 28f),
                cornerRadius = CornerRadius(4f, 4f),
            )
            y += 56f
        }

        // Map meters → canvas: ego at bottom-center; ahead = up; right = right
        val maxAhead = 50f
        val maxLat = 18f
        fun toCanvas(actor: SurroundActor): Offset {
            val nx = ((actor.xM / maxLat) * 0.5f + 0.5f).coerceIn(0.05f, 0.95f)
            val ny = (1f - (actor.yM / maxAhead)).coerceIn(0.05f, 0.88f)
            return Offset(nx * w, ny * h)
        }

        for (actor in actors.filter { it.yM > -5f && kotlin.math.abs(it.xM) < 25f }) {
            val p = toCanvas(actor)
            when (actor.kind) {
                ActorKind.PERSON -> {
                    // small standing figure
                    drawCircle(Color(0xFFFFCC80), radius = 8f, center = p)
                    drawRoundRect(
                        Color(0xFFFFB74D),
                        topLeft = Offset(p.x - 5f, p.y - 22f),
                        size = Size(10f, 18f),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                }
                ActorKind.MOTORCYCLE, ActorKind.BICYCLE -> {
                    drawRoundRect(
                        Color(0xFF80CBC4),
                        topLeft = Offset(p.x - 14f, p.y - 10f),
                        size = Size(28f, 16f),
                        cornerRadius = CornerRadius(8f, 8f),
                    )
                    drawCircle(Color(0xFF004D40), radius = 5f, center = Offset(p.x - 10f, p.y + 6f))
                    drawCircle(Color(0xFF004D40), radius = 5f, center = Offset(p.x + 10f, p.y + 6f))
                }
                ActorKind.TRUCK, ActorKind.BUS -> {
                    val bw = w * 0.14f
                    val bh = h * 0.16f
                    drawRoundRect(
                        Color(0xFF78909C),
                        topLeft = Offset(p.x - bw / 2, p.y - bh / 2),
                        size = Size(bw, bh),
                        cornerRadius = CornerRadius(12f, 12f),
                    )
                }
                ActorKind.CAR, ActorKind.UNKNOWN -> {
                    val bw = w * 0.11f
                    val bh = h * 0.12f
                    drawRoundRect(
                        Color(0xFF9E9E9E),
                        topLeft = Offset(p.x - bw / 2, p.y - bh / 2),
                        size = Size(bw, bh),
                        cornerRadius = CornerRadius(14f, 14f),
                    )
                }
            }
        }

        // Ego car (white) — always bottom-center
        val carW = w * 0.16f
        val carH = h * 0.22f
        drawRoundRect(
            color = Color(0xFFE8E8E8),
            topLeft = Offset(w * 0.42f, h * 0.72f),
            size = Size(carW, carH),
            cornerRadius = CornerRadius(18f, 18f),
        )
    }
}
