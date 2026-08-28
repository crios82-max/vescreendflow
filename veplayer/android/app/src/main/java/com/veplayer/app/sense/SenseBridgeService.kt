package com.veplayer.app.sense

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.veplayer.app.MainActivity
import com.veplayer.app.R
import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetClient
import com.veplayer.app.fleet.RemoteCommandExecutor
import com.veplayer.app.ota.SilentOtaCoordinator
import com.veplayer.app.surround.SenseflowSurroundClient
import com.veplayer.app.surround.SurroundEngine
import com.veplayer.app.vehicle.CanBusManager
import com.veplayer.app.vehicle.VehicleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** SenseFlow pings + fleet heartbeat + remote commands. */
class SenseBridgeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private lateinit var prefs: VePrefs
    private lateinit var fleet: FleetClient
    private lateinit var remote: RemoteCommandExecutor
    private lateinit var surroundClient: SenseflowSurroundClient

    private val callback =
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val speed = if (loc.hasSpeed()) loc.speed else null
                val heading = if (loc.hasBearing()) loc.bearing else null
                CanBusManager.ingestGps(
                    speedMps = speed,
                    headingDeg = heading,
                    reverseOverride = prefs.mockReverse && prefs.signalSource == "gps",
                )
                if (prefs.navEnabled) {
                    prefs.navFromLat = loc.latitude
                    prefs.navFromLng = loc.longitude
                }
                val snap = VehicleState.state.value
                val activity =
                    when {
                        snap.speedMps >= 4f || (speed != null && speed >= 4f) -> "IN_VEHICLE"
                        snap.speedMps >= 0.5f || (speed != null && speed >= 0.5f) -> "ON_FOOT"
                        speed == null && snap.source == "idle" -> "UNKNOWN"
                        else -> "STILL"
                    }
                scope.launch {
                    postPing(
                        loc.latitude,
                        loc.longitude,
                        loc.accuracy,
                        snap.speedMps.takeIf { it > 0f } ?: speed,
                        activity,
                    )
                    runCatching {
                        if (prefs.pairCodeCached() == null) fleet.register()
                        val hb =
                            fleet.heartbeat(
                                lat = loc.latitude,
                                lng = loc.longitude,
                                speedMps = snap.speedMps,
                                reverse = snap.reverse,
                                vehicleSignals =
                                    snap.toJsonMap() +
                                        mapOf(
                                            "idle_sec" to com.veplayer.app.vehicle.IdleMonitor.state.value.idleForSec.toDouble(),
                                            "idle_band" to com.veplayer.app.vehicle.IdleMonitor.state.value.band,
                                            "shift_duration_sec" to
                                                com.veplayer.app.vehicle.ShiftFatigueMonitor.state.value.durationSec.toDouble(),
                                            "shift_band" to com.veplayer.app.vehicle.ShiftFatigueMonitor.state.value.band,
                                    "rest_drive_sec" to
                                        com.veplayer.app.vehicle.RestBreakMonitor.state.value.drivingSec.toDouble(),
                                    "rest_warn_sec" to (prefs.restDriveWarnMin * 60f).toDouble(),
                                    "rest_alert_sec" to (prefs.restDriveAlertMin * 60f).toDouble(),
                                    "route_off_m" to
                                        com.veplayer.app.vehicle.RouteDeviationMonitor.state.value.distanceM.toDouble(),
                                    "route_warn_m" to prefs.routeDevWarnM.toDouble(),
                                    "route_alert_m" to prefs.routeDevAlertM.toDouble(),
                                    "route_dev" to
                                        com.veplayer.app.vehicle.RouteDeviation.toJsonMap(
                                            com.veplayer.app.vehicle.RouteDeviationMonitor.state.value,
                                        ),
                                    "driver_score" to
                                        com.veplayer.app.vehicle.DriverScore.toJsonMap(
                                            com.veplayer.app.vehicle.DriverScoreMonitor.state.value,
                                        ),
                                    "driver_score_warn" to prefs.driverScoreWarn.toDouble(),
                                    "driver_score_alert" to prefs.driverScoreAlert.toDouble(),
                                    "eco_live" to
                                        com.veplayer.app.vehicle.EcoLive.toJsonMap(
                                            com.veplayer.app.vehicle.EcoLiveMonitor.state.value,
                                        ),
                                    "eco_score" to
                                        com.veplayer.app.vehicle.EcoLiveMonitor.state.value.score.takeIf {
                                            com.veplayer.app.vehicle.EcoLiveMonitor.state.value.active
                                        },
                                    "eco_band" to
                                        com.veplayer.app.vehicle.EcoLiveMonitor.state.value.band.takeIf {
                                            com.veplayer.app.vehicle.EcoLiveMonitor.state.value.active
                                        },
                                    "eco_warn_score" to prefs.ecoLiveWarn.toDouble(),
                                    "eco_alert_score" to prefs.ecoLiveAlert.toDouble(),
                                    "runtime_sec" to
                                        (if (prefs.engineRuntimeSimHours > 0f)
                                            (prefs.engineRuntimeSimHours * 3600f).toInt()
                                        else
                                            com.veplayer.app.vehicle.EngineRuntimeMonitor.state.value.runtimeSec
                                                ?: snap.runtimeSec
                                        ),
                                    "runtime_warn_sec" to (prefs.engineRuntimeWarnHours * 3600f).toDouble(),
                                    "runtime_alert_sec" to (prefs.engineRuntimeAlertHours * 3600f).toDouble(),
                                    "engine_runtime" to
                                        com.veplayer.app.vehicle.EngineRuntime.toJsonMap(
                                            com.veplayer.app.vehicle.EngineRuntimeMonitor.state.value,
                                        ),
                                    "cabin_warn_c" to prefs.cabinWarnC.toDouble(),
                                    "cabin_alert_c" to prefs.cabinAlertC.toDouble(),
                                    "ice_warn_c" to prefs.iceWarnC.toDouble(),
                                    "ice_alert_c" to prefs.iceAlertC.toDouble(),
                                    "outdoor_temp_c" to
                                        (if (prefs.iceSimOn) prefs.iceSimC
                                        else com.veplayer.app.vehicle.IceFrostMonitor.state.value.outdoorC
                                            ?: snap.outdoorTempC
                                        )?.toDouble(),
                                    "coolant_warn_c" to prefs.coolantWarnC.toDouble(),
                                    "coolant_alert_c" to prefs.coolantAlertC.toDouble(),
                                    "coolant_c" to
                                        (if (prefs.coolantSimC > 0f) prefs.coolantSimC
                                        else com.veplayer.app.vehicle.VehicleState.state.value.coolantC
                                        )?.toDouble(),
                                    "oil_temp_warn_c" to prefs.oilTempWarnC.toDouble(),
                                    "oil_temp_alert_c" to prefs.oilTempAlertC.toDouble(),
                                    "oil_temp_c" to
                                        (if (prefs.oilTempSimC > 0f) prefs.oilTempSimC
                                        else com.veplayer.app.vehicle.OilTempMonitor.state.value.oilTempC
                                            ?: snap.oilTempC
                                        )?.toDouble(),
                                    "oil_temp" to
                                        com.veplayer.app.vehicle.OilTemp.toJsonMap(
                                            com.veplayer.app.vehicle.OilTempMonitor.state.value,
                                        ),
                                    "catalyst_warn_c" to prefs.catalystWarnC.toDouble(),
                                    "catalyst_alert_c" to prefs.catalystAlertC.toDouble(),
                                    "catalyst_temp_c" to
                                        (if (prefs.catalystSimC > 0f) prefs.catalystSimC
                                        else com.veplayer.app.vehicle.CatalystTempMonitor.state.value.catalystTempC
                                            ?: snap.catalystTempC
                                        )?.toDouble(),
                                    "catalyst_temp" to
                                        com.veplayer.app.vehicle.CatalystTemp.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystTempMonitor.state.value,
                                        ),
                                    "intake_air_warn_c" to prefs.intakeAirWarnC.toDouble(),
                                    "intake_air_alert_c" to prefs.intakeAirAlertC.toDouble(),
                                    "intake_air_c" to
                                        (if (prefs.intakeAirSimC > 0f) prefs.intakeAirSimC
                                        else com.veplayer.app.vehicle.IntakeAirMonitor.state.value.intakeAirC
                                            ?: snap.intakeAirC
                                        )?.toDouble(),
                                    "intake_air" to
                                        com.veplayer.app.vehicle.IntakeAir.toJsonMap(
                                            com.veplayer.app.vehicle.IntakeAirMonitor.state.value,
                                        ),
                                    "fuel_rate_warn_lph" to prefs.fuelRateWarnLph.toDouble(),
                                    "fuel_rate_alert_lph" to prefs.fuelRateAlertLph.toDouble(),
                                    "fuel_rate_speed_min_kmh" to prefs.fuelRateSpeedMinKmh.toDouble(),
                                    "fuel_rate_gps" to
                                        (if (prefs.fuelRateSimLph > 0f)
                                            com.veplayer.app.vehicle.FuelRate.lphToGps(prefs.fuelRateSimLph)
                                        else
                                            com.veplayer.app.vehicle.FuelRateMonitor.state.value.fuelRateGps
                                                ?: snap.fuelRateGps
                                        )?.toDouble(),
                                    "fuel_rate_lph" to
                                        com.veplayer.app.vehicle.FuelRateMonitor.state.value.fuelRateLph?.toDouble(),
                                    "fuel_rate" to
                                        com.veplayer.app.vehicle.FuelRate.toJsonMap(
                                            com.veplayer.app.vehicle.FuelRateMonitor.state.value,
                                        ),
                                    "maf_warn_gps" to prefs.mafWarnGps.toDouble(),
                                    "maf_alert_gps" to prefs.mafAlertGps.toDouble(),
                                    "maf_speed_min_kmh" to prefs.mafSpeedMinKmh.toDouble(),
                                    "maf_gps" to
                                        (if (prefs.mafSimGps > 0f) prefs.mafSimGps
                                        else com.veplayer.app.vehicle.MafAirflowMonitor.state.value.mafGps
                                            ?: snap.mafGps
                                        )?.toDouble(),
                                    "maf_airflow" to
                                        com.veplayer.app.vehicle.MafAirflow.toJsonMap(
                                            com.veplayer.app.vehicle.MafAirflowMonitor.state.value,
                                        ),
                                    "fuel_press_warn_kpa" to prefs.fuelPressWarnKpa.toDouble(),
                                    "fuel_press_alert_kpa" to prefs.fuelPressAlertKpa.toDouble(),
                                    "fuel_press_speed_min_kmh" to prefs.fuelPressSpeedMinKmh.toDouble(),
                                    "fuel_pressure_kpa" to
                                        (if (prefs.fuelPressSimKpa > 0f) prefs.fuelPressSimKpa
                                        else com.veplayer.app.vehicle.FuelPressureMonitor.state.value.pressureKpa
                                            ?: snap.fuelPressureKpa
                                        )?.toDouble(),
                                    "fuel_pressure" to
                                        com.veplayer.app.vehicle.FuelPressure.toJsonMap(
                                            com.veplayer.app.vehicle.FuelPressureMonitor.state.value,
                                        ),
                                    "baro_warn_low_kpa" to prefs.baroWarnLowKpa.toDouble(),
                                    "baro_alert_low_kpa" to prefs.baroAlertLowKpa.toDouble(),
                                    "baro_warn_high_kpa" to prefs.baroWarnHighKpa.toDouble(),
                                    "baro_alert_high_kpa" to prefs.baroAlertHighKpa.toDouble(),
                                    "baro_speed_min_kmh" to prefs.baroSpeedMinKmh.toDouble(),
                                    "baro_kpa" to
                                        (if (prefs.baroSimKpa > 0f) prefs.baroSimKpa
                                        else com.veplayer.app.vehicle.BarometricPressureMonitor.state.value.baroKpa
                                            ?: snap.baroKpa
                                        )?.toDouble(),
                                    "barometric" to
                                        com.veplayer.app.vehicle.BarometricPressure.toJsonMap(
                                            com.veplayer.app.vehicle.BarometricPressureMonitor.state.value,
                                        ),
                                    "timing_warn_deg" to prefs.timingWarnDeg.toDouble(),
                                    "timing_alert_deg" to prefs.timingAlertDeg.toDouble(),
                                    "timing_speed_min_kmh" to prefs.timingSpeedMinKmh.toDouble(),
                                    "timing_rpm_min" to prefs.timingRpmMin.toDouble(),
                                    "timing_advance_deg" to
                                        (if (prefs.timingSimDeg != 0f) prefs.timingSimDeg
                                        else com.veplayer.app.vehicle.TimingAdvanceMonitor.state.value.timingDeg
                                            ?: snap.timingAdvanceDeg
                                        )?.toDouble(),
                                    "timing_advance" to
                                        com.veplayer.app.vehicle.TimingAdvance.toJsonMap(
                                            com.veplayer.app.vehicle.TimingAdvanceMonitor.state.value,
                                        ),
                                    "o2_warn_low_v" to prefs.o2WarnLowV.toDouble(),
                                    "o2_alert_low_v" to prefs.o2AlertLowV.toDouble(),
                                    "o2_warn_high_v" to prefs.o2WarnHighV.toDouble(),
                                    "o2_alert_high_v" to prefs.o2AlertHighV.toDouble(),
                                    "o2_speed_min_kmh" to prefs.o2SpeedMinKmh.toDouble(),
                                    "o2_rpm_min" to prefs.o2RpmMin.toDouble(),
                                    "o2_b1s1_volts" to
                                        (if (prefs.o2SimVolts > 0f) prefs.o2SimVolts
                                        else com.veplayer.app.vehicle.O2VoltageMonitor.state.value.o2Volts
                                            ?: snap.o2B1s1Volts
                                        )?.toDouble(),
                                    "o2_voltage" to
                                        com.veplayer.app.vehicle.O2Voltage.toJsonMap(
                                            com.veplayer.app.vehicle.O2VoltageMonitor.state.value,
                                        ),
                                    "abs_load_warn_pct" to prefs.absLoadWarnPct.toDouble(),
                                    "abs_load_alert_pct" to prefs.absLoadAlertPct.toDouble(),
                                    "abs_load_speed_min_kmh" to prefs.absLoadSpeedMinKmh.toDouble(),
                                    "absolute_load_pct" to
                                        (if (prefs.absLoadSimPct > 0f) prefs.absLoadSimPct
                                        else com.veplayer.app.vehicle.AbsoluteLoadMonitor.state.value.loadPct
                                            ?: snap.absoluteLoadPct
                                        )?.toDouble(),
                                    "absolute_load" to
                                        com.veplayer.app.vehicle.AbsoluteLoad.toJsonMap(
                                            com.veplayer.app.vehicle.AbsoluteLoadMonitor.state.value,
                                        ),
                                    "rel_thr_warn_pct" to prefs.relThrWarnPct.toDouble(),
                                    "rel_thr_alert_pct" to prefs.relThrAlertPct.toDouble(),
                                    "rel_thr_speed_min_kmh" to prefs.relThrSpeedMinKmh.toDouble(),
                                    "relative_throttle_pct" to
                                        (if (prefs.relThrSimPct > 0f) prefs.relThrSimPct
                                        else com.veplayer.app.vehicle.RelativeThrottleMonitor.state.value.throttlePct
                                            ?: snap.relativeThrottlePct
                                        )?.toDouble(),
                                    "relative_throttle" to
                                        com.veplayer.app.vehicle.RelativeThrottle.toJsonMap(
                                            com.veplayer.app.vehicle.RelativeThrottleMonitor.state.value,
                                        ),
                                    "accel_pedal_warn_pct" to prefs.accelPedalWarnPct.toDouble(),
                                    "accel_pedal_alert_pct" to prefs.accelPedalAlertPct.toDouble(),
                                    "accel_pedal_speed_min_kmh" to prefs.accelPedalSpeedMinKmh.toDouble(),
                                    "accel_pedal_pct" to
                                        (if (prefs.accelPedalSimPct > 0f) prefs.accelPedalSimPct
                                        else com.veplayer.app.vehicle.AccelPedalMonitor.state.value.pedalPct
                                            ?: snap.accelPedalPct
                                        )?.toDouble(),
                                    "accel_pedal" to
                                        com.veplayer.app.vehicle.AccelPedal.toJsonMap(
                                            com.veplayer.app.vehicle.AccelPedalMonitor.state.value,
                                        ),
                                    "o2_b2_warn_low_v" to prefs.o2B2WarnLowV.toDouble(),
                                    "o2_b2_alert_low_v" to prefs.o2B2AlertLowV.toDouble(),
                                    "o2_b2_warn_high_v" to prefs.o2B2WarnHighV.toDouble(),
                                    "o2_b2_alert_high_v" to prefs.o2B2AlertHighV.toDouble(),
                                    "o2_b2_speed_min_kmh" to prefs.o2B2SpeedMinKmh.toDouble(),
                                    "o2_b2_rpm_min" to prefs.o2B2RpmMin.toDouble(),
                                    "o2_b1s2_volts" to
                                        (if (prefs.o2B2SimVolts > 0f) prefs.o2B2SimVolts
                                        else com.veplayer.app.vehicle.O2B2VoltageMonitor.state.value.o2Volts
                                            ?: snap.o2B1s2Volts
                                        )?.toDouble(),
                                    "o2_b2_voltage" to
                                        com.veplayer.app.vehicle.O2B2Voltage.toJsonMap(
                                            com.veplayer.app.vehicle.O2B2VoltageMonitor.state.value,
                                        ),
                                    "egr_warn_pct" to prefs.egrWarnPct.toDouble(),
                                    "egr_alert_pct" to prefs.egrAlertPct.toDouble(),
                                    "egr_speed_min_kmh" to prefs.egrSpeedMinKmh.toDouble(),
                                    "egr_error_pct" to
                                        (if (prefs.egrSimPct != 0f) prefs.egrSimPct
                                        else com.veplayer.app.vehicle.EgrErrorMonitor.state.value.errorPct
                                            ?: snap.egrErrorPct
                                        )?.toDouble(),
                                    "egr_error" to
                                        com.veplayer.app.vehicle.EgrError.toJsonMap(
                                            com.veplayer.app.vehicle.EgrErrorMonitor.state.value,
                                        ),
                                    "equiv_warn_low" to prefs.equivWarnLow.toDouble(),
                                    "equiv_alert_low" to prefs.equivAlertLow.toDouble(),
                                    "equiv_warn_high" to prefs.equivWarnHigh.toDouble(),
                                    "equiv_alert_high" to prefs.equivAlertHigh.toDouble(),
                                    "equiv_speed_min_kmh" to prefs.equivSpeedMinKmh.toDouble(),
                                    "equiv_rpm_min" to prefs.equivRpmMin.toDouble(),
                                    "equiv_ratio" to
                                        (if (prefs.equivSimRatio > 0f) prefs.equivSimRatio
                                        else com.veplayer.app.vehicle.EquivRatioMonitor.state.value.ratio
                                            ?: snap.equivRatio
                                        )?.toDouble(),
                                    "equiv_ratio_state" to
                                        com.veplayer.app.vehicle.EquivRatio.toJsonMap(
                                            com.veplayer.app.vehicle.EquivRatioMonitor.state.value,
                                        ),
                                    "evap_purge_warn_pct" to prefs.evapPurgeWarnPct.toDouble(),
                                    "evap_purge_alert_pct" to prefs.evapPurgeAlertPct.toDouble(),
                                    "evap_purge_speed_min_kmh" to prefs.evapPurgeSpeedMinKmh.toDouble(),
                                    "evap_purge_pct" to
                                        (if (prefs.evapPurgeSimPct > 0f) prefs.evapPurgeSimPct
                                        else com.veplayer.app.vehicle.EvapPurgeMonitor.state.value.purgePct
                                            ?: snap.evapPurgePct
                                        )?.toDouble(),
                                    "evap_purge" to
                                        com.veplayer.app.vehicle.EvapPurge.toJsonMap(
                                            com.veplayer.app.vehicle.EvapPurgeMonitor.state.value,
                                        ),
                                    "ethanol_warn_pct" to prefs.ethanolWarnPct.toDouble(),
                                    "ethanol_alert_pct" to prefs.ethanolAlertPct.toDouble(),
                                    "ethanol_speed_min_kmh" to prefs.ethanolSpeedMinKmh.toDouble(),
                                    "ethanol_pct" to
                                        (if (prefs.ethanolSimPct > 0f) prefs.ethanolSimPct
                                        else com.veplayer.app.vehicle.EthanolPctMonitor.state.value.ethanolPct
                                            ?: snap.ethanolPct
                                        )?.toDouble(),
                                    "ethanol" to
                                        com.veplayer.app.vehicle.EthanolPct.toJsonMap(
                                            com.veplayer.app.vehicle.EthanolPctMonitor.state.value,
                                        ),
                                    "evap_vapor_warn_pa" to prefs.evapVaporWarnPa.toDouble(),
                                    "evap_vapor_alert_pa" to prefs.evapVaporAlertPa.toDouble(),
                                    "evap_vapor_speed_min_kmh" to prefs.evapVaporSpeedMinKmh.toDouble(),
                                    "evap_vapor_pa" to
                                        (if (prefs.evapVaporSimPa != 0f) prefs.evapVaporSimPa
                                        else com.veplayer.app.vehicle.EvapVaporMonitor.state.value.pressurePa
                                            ?: snap.evapVaporPa
                                        )?.toDouble(),
                                    "evap_vapor" to
                                        com.veplayer.app.vehicle.EvapVapor.toJsonMap(
                                            com.veplayer.app.vehicle.EvapVaporMonitor.state.value,
                                        ),
                                    "rail_abs_warn_kpa" to prefs.railAbsWarnKpa.toDouble(),
                                    "rail_abs_alert_kpa" to prefs.railAbsAlertKpa.toDouble(),
                                    "rail_abs_speed_min_kmh" to prefs.railAbsSpeedMinKmh.toDouble(),
                                    "fuel_rail_abs_kpa" to
                                        (if (prefs.railAbsSimKpa > 0f) prefs.railAbsSimKpa
                                        else com.veplayer.app.vehicle.FuelRailAbsMonitor.state.value.pressureKpa
                                            ?: snap.fuelRailAbsKpa
                                        )?.toDouble(),
                                    "fuel_rail_abs" to
                                        com.veplayer.app.vehicle.FuelRailAbs.toJsonMap(
                                            com.veplayer.app.vehicle.FuelRailAbsMonitor.state.value,
                                        ),
                                    "egr_cmd_warn_pct" to prefs.egrCmdWarnPct.toDouble(),
                                    "egr_cmd_alert_pct" to prefs.egrCmdAlertPct.toDouble(),
                                    "egr_cmd_speed_min_kmh" to prefs.egrCmdSpeedMinKmh.toDouble(),
                                    "egr_cmd_pct" to
                                        (if (prefs.egrCmdSimPct > 0f) prefs.egrCmdSimPct
                                        else com.veplayer.app.vehicle.CommandedEgrMonitor.state.value.egrPct
                                            ?: snap.egrCmdPct
                                        )?.toDouble(),
                                    "egr_cmd" to
                                        com.veplayer.app.vehicle.CommandedEgr.toJsonMap(
                                            com.veplayer.app.vehicle.CommandedEgrMonitor.state.value,
                                        ),
                                    "rel_aped_warn_pct" to prefs.relApedWarnPct.toDouble(),
                                    "rel_aped_alert_pct" to prefs.relApedAlertPct.toDouble(),
                                    "rel_aped_speed_min_kmh" to prefs.relApedSpeedMinKmh.toDouble(),
                                    "rel_accel_pedal_pct" to
                                        (if (prefs.relApedSimPct > 0f) prefs.relApedSimPct
                                        else com.veplayer.app.vehicle.RelAccelPedalMonitor.state.value.pedalPct
                                            ?: snap.relAccelPedalPct
                                        )?.toDouble(),
                                    "rel_aped" to
                                        com.veplayer.app.vehicle.RelAccelPedal.toJsonMap(
                                            com.veplayer.app.vehicle.RelAccelPedalMonitor.state.value,
                                        ),
                                    "drv_torque_warn_pct" to prefs.drvTorqueWarnPct.toDouble(),
                                    "drv_torque_alert_pct" to prefs.drvTorqueAlertPct.toDouble(),
                                    "drv_torque_speed_min_kmh" to prefs.drvTorqueSpeedMinKmh.toDouble(),
                                    "driver_torque_pct" to
                                        (if (prefs.drvTorqueSimPct != 0f) prefs.drvTorqueSimPct
                                        else com.veplayer.app.vehicle.DriverTorqueMonitor.state.value.torquePct
                                            ?: snap.driverTorquePct
                                        )?.toDouble(),
                                    "drv_torque" to
                                        com.veplayer.app.vehicle.DriverTorque.toJsonMap(
                                            com.veplayer.app.vehicle.DriverTorqueMonitor.state.value,
                                        ),
                                    "act_torque_warn_pct" to prefs.actTorqueWarnPct.toDouble(),
                                    "act_torque_alert_pct" to prefs.actTorqueAlertPct.toDouble(),
                                    "act_torque_speed_min_kmh" to prefs.actTorqueSpeedMinKmh.toDouble(),
                                    "actual_torque_pct" to
                                        (if (prefs.actTorqueSimPct != 0f) prefs.actTorqueSimPct
                                        else com.veplayer.app.vehicle.ActualTorqueMonitor.state.value.torquePct
                                            ?: snap.actualTorquePct
                                        )?.toDouble(),
                                    "act_torque" to
                                        com.veplayer.app.vehicle.ActualTorque.toJsonMap(
                                            com.veplayer.app.vehicle.ActualTorqueMonitor.state.value,
                                        ),
                                    "cat_b2_warn_c" to prefs.catB2WarnC.toDouble(),
                                    "cat_b2_alert_c" to prefs.catB2AlertC.toDouble(),
                                    "catalyst_b2_temp_c" to
                                        (if (prefs.catB2SimC > 0f) prefs.catB2SimC
                                        else com.veplayer.app.vehicle.CatalystB2Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2TempC
                                        )?.toDouble(),
                                    "catalyst_b2" to
                                        com.veplayer.app.vehicle.CatalystB2.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2Monitor.state.value,
                                        ),
                                    "cat_b1s2_warn_c" to prefs.catB1s2WarnC.toDouble(),
                                    "cat_b1s2_alert_c" to prefs.catB1s2AlertC.toDouble(),
                                    "catalyst_b1s2_temp_c" to
                                        (if (prefs.catB1s2SimC > 0f) prefs.catB1s2SimC
                                        else com.veplayer.app.vehicle.CatalystB1S2Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s2TempC
                                        )?.toDouble(),
                                    "catalyst_b1s2" to
                                        com.veplayer.app.vehicle.CatalystB1S2.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S2Monitor.state.value,
                                        ),
                                    "cat_b2s2_warn_c" to prefs.catB2s2WarnC.toDouble(),
                                    "cat_b2s2_alert_c" to prefs.catB2s2AlertC.toDouble(),
                                    "catalyst_b2s2_temp_c" to
                                        (if (prefs.catB2s2SimC > 0f) prefs.catB2s2SimC
                                        else com.veplayer.app.vehicle.CatalystB2S2Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s2TempC
                                        )?.toDouble(),
                                    "catalyst_b2s2" to
                                        com.veplayer.app.vehicle.CatalystB2S2.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S2Monitor.state.value,
                                        ),
                                    "cat_b1s3_warn_c" to prefs.catB1s3WarnC.toDouble(),
                                    "cat_b1s3_alert_c" to prefs.catB1s3AlertC.toDouble(),
                                    "catalyst_b1s3_temp_c" to
                                        (if (prefs.catB1s3SimC > 0f) prefs.catB1s3SimC
                                        else com.veplayer.app.vehicle.CatalystB1S3Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s3TempC
                                        )?.toDouble(),
                                    "catalyst_b1s3" to
                                        com.veplayer.app.vehicle.CatalystB1S3.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S3Monitor.state.value,
                                        ),
                                    "cat_b2s3_warn_c" to prefs.catB2s3WarnC.toDouble(),
                                    "cat_b2s3_alert_c" to prefs.catB2s3AlertC.toDouble(),
                                    "catalyst_b2s3_temp_c" to
                                        (if (prefs.catB2s3SimC > 0f) prefs.catB2s3SimC
                                        else com.veplayer.app.vehicle.CatalystB2S3Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s3TempC
                                        )?.toDouble(),
                                    "catalyst_b2s3" to
                                        com.veplayer.app.vehicle.CatalystB2S3.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S3Monitor.state.value,
                                        ),
                                    "cat_b1s4_warn_c" to prefs.catB1s4WarnC.toDouble(),
                                    "cat_b1s4_alert_c" to prefs.catB1s4AlertC.toDouble(),
                                    "catalyst_b1s4_temp_c" to
                                        (if (prefs.catB1s4SimC > 0f) prefs.catB1s4SimC
                                        else com.veplayer.app.vehicle.CatalystB1S4Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s4TempC
                                        )?.toDouble(),
                                    "catalyst_b1s4" to
                                        com.veplayer.app.vehicle.CatalystB1S4.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S4Monitor.state.value,
                                        ),
                                    "cat_b2s4_warn_c" to prefs.catB2s4WarnC.toDouble(),
                                    "cat_b2s4_alert_c" to prefs.catB2s4AlertC.toDouble(),
                                    "catalyst_b2s4_temp_c" to
                                        (if (prefs.catB2s4SimC > 0f) prefs.catB2s4SimC
                                        else com.veplayer.app.vehicle.CatalystB2S4Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s4TempC
                                        )?.toDouble(),
                                    "catalyst_b2s4" to
                                        com.veplayer.app.vehicle.CatalystB2S4.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S4Monitor.state.value,
                                        ),
                                    "stft2_b1_warn_pct" to prefs.stft2B1WarnPct.toDouble(),
                                    "stft2_b1_alert_pct" to prefs.stft2B1AlertPct.toDouble(),
                                    "stft2_b1_speed_min_kmh" to prefs.stft2B1SpeedMinKmh.toDouble(),
                                    "fuel_trim_stft2_b1_pct" to
                                        (if (prefs.stft2B1SimPct != 0f) prefs.stft2B1SimPct
                                        else com.veplayer.app.vehicle.FuelTrimStft2B1Monitor.state.value.trimPct
                                            ?: snap.fuelTrimStft2B1Pct
                                        )?.toDouble(),
                                    "stft2_b1" to
                                        com.veplayer.app.vehicle.FuelTrimStft2B1.toJsonMap(
                                            com.veplayer.app.vehicle.FuelTrimStft2B1Monitor.state.value,
                                        ),
                                    "ltft2_b1_warn_pct" to prefs.ltft2B1WarnPct.toDouble(),
                                    "ltft2_b1_alert_pct" to prefs.ltft2B1AlertPct.toDouble(),
                                    "ltft2_b1_speed_min_kmh" to prefs.ltft2B1SpeedMinKmh.toDouble(),
                                    "fuel_trim_ltft2_b1_pct" to
                                        (if (prefs.ltft2B1SimPct != 0f) prefs.ltft2B1SimPct
                                        else com.veplayer.app.vehicle.FuelTrimLtft2B1Monitor.state.value.trimPct
                                            ?: snap.fuelTrimLtft2B1Pct
                                        )?.toDouble(),
                                    "ltft2_b1" to
                                        com.veplayer.app.vehicle.FuelTrimLtft2B1.toJsonMap(
                                            com.veplayer.app.vehicle.FuelTrimLtft2B1Monitor.state.value,
                                        ),
                                    "stft2_b2_warn_pct" to prefs.stft2B2WarnPct.toDouble(),
                                    "stft2_b2_alert_pct" to prefs.stft2B2AlertPct.toDouble(),
                                    "stft2_b2_speed_min_kmh" to prefs.stft2B2SpeedMinKmh.toDouble(),
                                    "fuel_trim_stft2_b2_pct" to
                                        (if (prefs.stft2B2SimPct != 0f) prefs.stft2B2SimPct
                                        else com.veplayer.app.vehicle.FuelTrimStft2B2Monitor.state.value.trimPct
                                            ?: snap.fuelTrimStft2B2Pct
                                        )?.toDouble(),
                                    "stft2_b2" to
                                        com.veplayer.app.vehicle.FuelTrimStft2B2.toJsonMap(
                                            com.veplayer.app.vehicle.FuelTrimStft2B2Monitor.state.value,
                                        ),
                                    "ltft2_b2_warn_pct" to prefs.ltft2B2WarnPct.toDouble(),
                                    "ltft2_b2_alert_pct" to prefs.ltft2B2AlertPct.toDouble(),
                                    "ltft2_b2_speed_min_kmh" to prefs.ltft2B2SpeedMinKmh.toDouble(),
                                    "fuel_trim_ltft2_b2_pct" to
                                        (if (prefs.ltft2B2SimPct != 0f) prefs.ltft2B2SimPct
                                        else com.veplayer.app.vehicle.FuelTrimLtft2B2Monitor.state.value.trimPct
                                            ?: snap.fuelTrimLtft2B2Pct
                                        )?.toDouble(),
                                    "ltft2_b2" to
                                        com.veplayer.app.vehicle.FuelTrimLtft2B2.toJsonMap(
                                            com.veplayer.app.vehicle.FuelTrimLtft2B2Monitor.state.value,
                                        ),
                                    "cat_b1s5_warn_c" to prefs.catB1s5WarnC.toDouble(),
                                    "cat_b1s5_alert_c" to prefs.catB1s5AlertC.toDouble(),
                                    "catalyst_b1s5_temp_c" to
                                        (if (prefs.catB1s5SimC > 0f) prefs.catB1s5SimC
                                        else com.veplayer.app.vehicle.CatalystB1S5Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s5TempC
                                        )?.toDouble(),
                                    "catalyst_b1s5" to
                                        com.veplayer.app.vehicle.CatalystB1S5.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S5Monitor.state.value,
                                        ),
                                    "cat_b2s5_warn_c" to prefs.catB2s5WarnC.toDouble(),
                                    "cat_b2s5_alert_c" to prefs.catB2s5AlertC.toDouble(),
                                    "catalyst_b2s5_temp_c" to
                                        (if (prefs.catB2s5SimC > 0f) prefs.catB2s5SimC
                                        else com.veplayer.app.vehicle.CatalystB2S5Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s5TempC
                                        )?.toDouble(),
                                    "catalyst_b2s5" to
                                        com.veplayer.app.vehicle.CatalystB2S5.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S5Monitor.state.value,
                                        ),
                                    "inject_warn_deg" to prefs.injectWarnDeg.toDouble(),
                                    "inject_alert_deg" to prefs.injectAlertDeg.toDouble(),
                                    "inject_speed_min_kmh" to prefs.injectSpeedMinKmh.toDouble(),
                                    "fuel_inject_timing_deg" to
                                        (if (prefs.injectSimDeg != 0f) prefs.injectSimDeg
                                        else com.veplayer.app.vehicle.FuelInjectTimingMonitor.state.value.timingDeg
                                            ?: snap.fuelInjectTimingDeg
                                        )?.toDouble(),
                                    "fuel_inject" to
                                        com.veplayer.app.vehicle.FuelInjectTiming.toJsonMap(
                                            com.veplayer.app.vehicle.FuelInjectTimingMonitor.state.value,
                                        ),
                                    "hybrid_warn_pct" to prefs.hybridWarnPct.toDouble(),
                                    "hybrid_alert_pct" to prefs.hybridAlertPct.toDouble(),
                                    "hybrid_speed_min_kmh" to prefs.hybridSpeedMinKmh.toDouble(),
                                    "hybrid_batt_life_pct" to
                                        (if (prefs.hybridSimPct > 0f) prefs.hybridSimPct
                                        else com.veplayer.app.vehicle.HybridBattLifeMonitor.state.value.lifePct
                                            ?: snap.hybridBattLifePct
                                        )?.toDouble(),
                                    "hybrid_batt" to
                                        com.veplayer.app.vehicle.HybridBattLife.toJsonMap(
                                            com.veplayer.app.vehicle.HybridBattLifeMonitor.state.value,
                                        ),
                                    "ref_torque_warn_low_nm" to prefs.refTorqueWarnLowNm.toDouble(),
                                    "ref_torque_alert_low_nm" to prefs.refTorqueAlertLowNm.toDouble(),
                                    "ref_torque_warn_high_nm" to prefs.refTorqueWarnHighNm.toDouble(),
                                    "ref_torque_alert_high_nm" to prefs.refTorqueAlertHighNm.toDouble(),
                                    "engine_ref_torque_nm" to
                                        (if (prefs.refTorqueSimNm > 0f) prefs.refTorqueSimNm
                                        else com.veplayer.app.vehicle.EngineRefTorqueMonitor.state.value.torqueNm
                                            ?: snap.engineRefTorqueNm
                                        )?.toDouble(),
                                    "ref_torque" to
                                        com.veplayer.app.vehicle.EngineRefTorque.toJsonMap(
                                            com.veplayer.app.vehicle.EngineRefTorqueMonitor.state.value,
                                        ),
                                    "cat_b1s6_warn_c" to prefs.catB1s6WarnC.toDouble(),
                                    "cat_b1s6_alert_c" to prefs.catB1s6AlertC.toDouble(),
                                    "catalyst_b1s6_temp_c" to
                                        (if (prefs.catB1s6SimC > 0f) prefs.catB1s6SimC
                                        else com.veplayer.app.vehicle.CatalystB1S6Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s6TempC
                                        )?.toDouble(),
                                    "catalyst_b1s6" to
                                        com.veplayer.app.vehicle.CatalystB1S6.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S6Monitor.state.value,
                                        ),
                                    "cat_b2s6_warn_c" to prefs.catB2s6WarnC.toDouble(),
                                    "cat_b2s6_alert_c" to prefs.catB2s6AlertC.toDouble(),
                                    "catalyst_b2s6_temp_c" to
                                        (if (prefs.catB2s6SimC > 0f) prefs.catB2s6SimC
                                        else com.veplayer.app.vehicle.CatalystB2S6Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s6TempC
                                        )?.toDouble(),
                                    "catalyst_b2s6" to
                                        com.veplayer.app.vehicle.CatalystB2S6.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S6Monitor.state.value,
                                        ),
                                    "thr_b_warn_pct" to prefs.thrBWarnPct.toDouble(),
                                    "thr_b_alert_pct" to prefs.thrBAlertPct.toDouble(),
                                    "thr_b_speed_min_kmh" to prefs.thrBSpeedMinKmh.toDouble(),
                                    "throttle_b_pct" to
                                        (if (prefs.thrBSimPct > 0f) prefs.thrBSimPct
                                        else com.veplayer.app.vehicle.ThrottleBMonitor.state.value.throttlePct
                                            ?: snap.throttleBPct
                                        )?.toDouble(),
                                    "throttle_b" to
                                        com.veplayer.app.vehicle.ThrottleB.toJsonMap(
                                            com.veplayer.app.vehicle.ThrottleBMonitor.state.value,
                                        ),
                                    "thr_c_warn_pct" to prefs.thrCWarnPct.toDouble(),
                                    "thr_c_alert_pct" to prefs.thrCAlertPct.toDouble(),
                                    "thr_c_speed_min_kmh" to prefs.thrCSpeedMinKmh.toDouble(),
                                    "throttle_c_pct" to
                                        (if (prefs.thrCSimPct > 0f) prefs.thrCSimPct
                                        else com.veplayer.app.vehicle.ThrottleCMonitor.state.value.throttlePct
                                            ?: snap.throttleCPct
                                        )?.toDouble(),
                                    "throttle_c" to
                                        com.veplayer.app.vehicle.ThrottleC.toJsonMap(
                                            com.veplayer.app.vehicle.ThrottleCMonitor.state.value,
                                        ),
                                    "mil_time_warn_min" to prefs.milTimeWarnMin,
                                    "mil_time_alert_min" to prefs.milTimeAlertMin,
                                    "mil_time_min" to
                                        (if (prefs.milTimeSimMin > 0) prefs.milTimeSimMin
                                        else com.veplayer.app.vehicle.MilTimeOnMonitor.state.value.minutes
                                            ?: snap.milTimeMin
                                        ),
                                    "mil_time" to
                                        com.veplayer.app.vehicle.MilTimeOn.toJsonMap(
                                            com.veplayer.app.vehicle.MilTimeOnMonitor.state.value,
                                        ),
                                    "cat_b1s7_warn_c" to prefs.catB1s7WarnC.toDouble(),
                                    "cat_b1s7_alert_c" to prefs.catB1s7AlertC.toDouble(),
                                    "catalyst_b1s7_temp_c" to
                                        (if (prefs.catB1s7SimC > 0f) prefs.catB1s7SimC
                                        else com.veplayer.app.vehicle.CatalystB1S7Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s7TempC
                                        )?.toDouble(),
                                    "catalyst_b1s7" to
                                        com.veplayer.app.vehicle.CatalystB1S7.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S7Monitor.state.value,
                                        ),
                                    "cat_b2s7_warn_c" to prefs.catB2s7WarnC.toDouble(),
                                    "cat_b2s7_alert_c" to prefs.catB2s7AlertC.toDouble(),
                                    "catalyst_b2s7_temp_c" to
                                        (if (prefs.catB2s7SimC > 0f) prefs.catB2s7SimC
                                        else com.veplayer.app.vehicle.CatalystB2S7Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s7TempC
                                        )?.toDouble(),
                                    "catalyst_b2s7" to
                                        com.veplayer.app.vehicle.CatalystB2S7.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S7Monitor.state.value,
                                        ),
                                    "fuel_type_expected" to prefs.fuelTypeExpected,
                                    "fuel_type_speed_min_kmh" to prefs.fuelTypeSpeedMinKmh.toDouble(),
                                    "fuel_type_code" to
                                        (if (prefs.fuelTypeSimCode > 0) prefs.fuelTypeSimCode
                                        else com.veplayer.app.vehicle.FuelTypeMonitor.state.value.typeCode
                                            ?: snap.fuelTypeCode
                                        ),
                                    "fuel_type" to
                                        com.veplayer.app.vehicle.FuelType.toJsonMap(
                                            com.veplayer.app.vehicle.FuelTypeMonitor.state.value,
                                        ),
                                    "max_equiv_warn_low" to prefs.maxEquivWarnLow.toDouble(),
                                    "max_equiv_alert_low" to prefs.maxEquivAlertLow.toDouble(),
                                    "max_equiv_warn_high" to prefs.maxEquivWarnHigh.toDouble(),
                                    "max_equiv_alert_high" to prefs.maxEquivAlertHigh.toDouble(),
                                    "max_equiv_ratio" to
                                        (if (prefs.maxEquivSimRatio != 0f) prefs.maxEquivSimRatio
                                        else com.veplayer.app.vehicle.MaxEquivRatioMonitor.state.value.ratio
                                            ?: snap.maxEquivRatio
                                        )?.toDouble(),
                                    "max_equiv" to
                                        com.veplayer.app.vehicle.MaxEquivRatio.toJsonMap(
                                            com.veplayer.app.vehicle.MaxEquivRatioMonitor.state.value,
                                        ),
                                    "max_maf_warn_low_gps" to prefs.maxMafWarnLowGps.toDouble(),
                                    "max_maf_alert_low_gps" to prefs.maxMafAlertLowGps.toDouble(),
                                    "max_maf_gps" to
                                        (if (prefs.maxMafSimGps > 0f) prefs.maxMafSimGps
                                        else com.veplayer.app.vehicle.MaxMafGpsMonitor.state.value.mafGps
                                            ?: snap.maxMafGps
                                        )?.toDouble(),
                                    "max_maf" to
                                        com.veplayer.app.vehicle.MaxMafGps.toJsonMap(
                                            com.veplayer.app.vehicle.MaxMafGpsMonitor.state.value,
                                        ),
                                    "cat_b1s8_warn_c" to prefs.catB1s8WarnC.toDouble(),
                                    "cat_b1s8_alert_c" to prefs.catB1s8AlertC.toDouble(),
                                    "catalyst_b1s8_temp_c" to
                                        (if (prefs.catB1s8SimC > 0f) prefs.catB1s8SimC
                                        else com.veplayer.app.vehicle.CatalystB1S8Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s8TempC
                                        )?.toDouble(),
                                    "catalyst_b1s8" to
                                        com.veplayer.app.vehicle.CatalystB1S8.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S8Monitor.state.value,
                                        ),
                                    "cat_b2s8_warn_c" to prefs.catB2s8WarnC.toDouble(),
                                    "cat_b2s8_alert_c" to prefs.catB2s8AlertC.toDouble(),
                                    "catalyst_b2s8_temp_c" to
                                        (if (prefs.catB2s8SimC > 0f) prefs.catB2s8SimC
                                        else com.veplayer.app.vehicle.CatalystB2S8Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s8TempC
                                        )?.toDouble(),
                                    "catalyst_b2s8" to
                                        com.veplayer.app.vehicle.CatalystB2S8.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S8Monitor.state.value,
                                        ),
                                    "max_avail_torque_warn_low" to prefs.maxAvailTorqueWarnLow.toDouble(),
                                    "max_avail_torque_alert_low" to prefs.maxAvailTorqueAlertLow.toDouble(),
                                    "max_avail_torque_speed_min_kmh" to prefs.maxAvailTorqueSpeedMinKmh.toDouble(),
                                    "max_avail_torque_pct" to
                                        (if (prefs.maxAvailTorqueSimPct != 0f) prefs.maxAvailTorqueSimPct
                                        else com.veplayer.app.vehicle.MaxAvailTorqueMonitor.state.value.torquePct
                                            ?: snap.maxAvailTorquePct
                                        )?.toDouble(),
                                    "max_avail_torque" to
                                        com.veplayer.app.vehicle.MaxAvailTorque.toJsonMap(
                                            com.veplayer.app.vehicle.MaxAvailTorqueMonitor.state.value,
                                        ),
                                    "maf_iat_warn_c" to prefs.mafIatWarnC.toDouble(),
                                    "maf_iat_alert_c" to prefs.mafIatAlertC.toDouble(),
                                    "maf_iat_speed_min_kmh" to prefs.mafIatSpeedMinKmh.toDouble(),
                                    "maf_sensor_iat_c" to
                                        (if (prefs.mafIatSimC > 0f) prefs.mafIatSimC
                                        else com.veplayer.app.vehicle.MafSensorIatMonitor.state.value.tempC
                                            ?: snap.mafSensorIatC
                                        )?.toDouble(),
                                    "maf_iat" to
                                        com.veplayer.app.vehicle.MafSensorIat.toJsonMap(
                                            com.veplayer.app.vehicle.MafSensorIatMonitor.state.value,
                                        ),
                                    "aux_input_alert_mask" to prefs.auxInputAlertMask,
                                    "aux_input_speed_min_kmh" to prefs.auxInputSpeedMinKmh.toDouble(),
                                    "aux_input_status" to
                                        (if (prefs.auxInputSimCode > 0) prefs.auxInputSimCode
                                        else com.veplayer.app.vehicle.AuxInputStatusMonitor.state.value.statusCode
                                            ?: snap.auxInputStatus
                                        ),
                                    "aux_input" to
                                        com.veplayer.app.vehicle.AuxInputStatus.toJsonMap(
                                            com.veplayer.app.vehicle.AuxInputStatusMonitor.state.value,
                                        ),
                                    "cat_b1s9_warn_c" to prefs.catB1s9WarnC.toDouble(),
                                    "cat_b1s9_alert_c" to prefs.catB1s9AlertC.toDouble(),
                                    "catalyst_b1s9_temp_c" to
                                        (if (prefs.catB1s9SimC > 0f) prefs.catB1s9SimC
                                        else com.veplayer.app.vehicle.CatalystB1S9Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s9TempC
                                        )?.toDouble(),
                                    "catalyst_b1s9" to
                                        com.veplayer.app.vehicle.CatalystB1S9.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S9Monitor.state.value,
                                        ),
                                    "cat_b2s9_warn_c" to prefs.catB2s9WarnC.toDouble(),
                                    "cat_b2s9_alert_c" to prefs.catB2s9AlertC.toDouble(),
                                    "catalyst_b2s9_temp_c" to
                                        (if (prefs.catB2s9SimC > 0f) prefs.catB2s9SimC
                                        else com.veplayer.app.vehicle.CatalystB2S9Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s9TempC
                                        )?.toDouble(),
                                    "catalyst_b2s9" to
                                        com.veplayer.app.vehicle.CatalystB2S9.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S9Monitor.state.value,
                                        ),
                                    "ect2_warn_c" to prefs.ect2WarnC.toDouble(),
                                    "ect2_alert_c" to prefs.ect2AlertC.toDouble(),
                                    "coolant_ect2_c" to
                                        (if (prefs.ect2SimC > 0f) prefs.ect2SimC
                                        else com.veplayer.app.vehicle.CoolantEct2Monitor.state.value.coolantC
                                            ?: snap.coolantEct2C
                                        )?.toDouble(),
                                    "ect2" to
                                        com.veplayer.app.vehicle.CoolantEct2.toJsonMap(
                                            com.veplayer.app.vehicle.CoolantEct2Monitor.state.value,
                                        ),
                                    "iat2_warn_c" to prefs.iat2WarnC.toDouble(),
                                    "iat2_alert_c" to prefs.iat2AlertC.toDouble(),
                                    "iat2_speed_min_kmh" to prefs.iat2SpeedMinKmh.toDouble(),
                                    "iat_sensor2_c" to
                                        (if (prefs.iat2SimC > 0f) prefs.iat2SimC
                                        else com.veplayer.app.vehicle.IatSensor2Monitor.state.value.tempC
                                            ?: snap.iatSensor2C
                                        )?.toDouble(),
                                    "iat2" to
                                        com.veplayer.app.vehicle.IatSensor2.toJsonMap(
                                            com.veplayer.app.vehicle.IatSensor2Monitor.state.value,
                                        ),
                                    "turbo_inlet_warn_kpa" to prefs.turboInletWarnKpa.toDouble(),
                                    "turbo_inlet_alert_kpa" to prefs.turboInletAlertKpa.toDouble(),
                                    "turbo_inlet_speed_min_kmh" to prefs.turboInletSpeedMinKmh.toDouble(),
                                    "turbo_inlet_kpa" to
                                        (if (prefs.turboInletSimKpa > 0f) prefs.turboInletSimKpa
                                        else com.veplayer.app.vehicle.TurboInletPressureMonitor.state.value.pressureKpa
                                            ?: snap.turboInletKpa
                                        )?.toDouble(),
                                    "turbo_inlet" to
                                        com.veplayer.app.vehicle.TurboInletPressure.toJsonMap(
                                            com.veplayer.app.vehicle.TurboInletPressureMonitor.state.value,
                                        ),
                                    "cat_b1s10_warn_c" to prefs.catB1s10WarnC.toDouble(),
                                    "cat_b1s10_alert_c" to prefs.catB1s10AlertC.toDouble(),
                                    "catalyst_b1s10_temp_c" to
                                        (if (prefs.catB1s10SimC > 0f) prefs.catB1s10SimC
                                        else com.veplayer.app.vehicle.CatalystB1S10Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s10TempC
                                        )?.toDouble(),
                                    "catalyst_b1s10" to
                                        com.veplayer.app.vehicle.CatalystB1S10.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S10Monitor.state.value,
                                        ),
                                    "cat_b2s10_warn_c" to prefs.catB2s10WarnC.toDouble(),
                                    "cat_b2s10_alert_c" to prefs.catB2s10AlertC.toDouble(),
                                    "catalyst_b2s10_temp_c" to
                                        (if (prefs.catB2s10SimC > 0f) prefs.catB2s10SimC
                                        else com.veplayer.app.vehicle.CatalystB2S10Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s10TempC
                                        )?.toDouble(),
                                    "catalyst_b2s10" to
                                        com.veplayer.app.vehicle.CatalystB2S10.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S10Monitor.state.value,
                                        ),
                                    "egr_temp_warn_c" to prefs.egrTempWarnC.toDouble(),
                                    "egr_temp_alert_c" to prefs.egrTempAlertC.toDouble(),
                                    "egr_temp_speed_min_kmh" to prefs.egrTempSpeedMinKmh.toDouble(),
                                    "egr_temp_c" to
                                        (if (prefs.egrTempSimC > 0f) prefs.egrTempSimC
                                        else com.veplayer.app.vehicle.EgrTemperatureMonitor.state.value.tempC
                                            ?: snap.egrTempC
                                        )?.toDouble(),
                                    "egr_temp" to
                                        com.veplayer.app.vehicle.EgrTemperature.toJsonMap(
                                            com.veplayer.app.vehicle.EgrTemperatureMonitor.state.value,
                                        ),
                                    "diesel_iaf_warn_pct" to prefs.dieselIafWarnPct.toDouble(),
                                    "diesel_iaf_alert_pct" to prefs.dieselIafAlertPct.toDouble(),
                                    "diesel_iaf_speed_min_kmh" to prefs.dieselIafSpeedMinKmh.toDouble(),
                                    "diesel_iaf_cmd_pct" to
                                        (if (prefs.dieselIafSimPct > 0f) prefs.dieselIafSimPct
                                        else com.veplayer.app.vehicle.DieselIntakeAirflowMonitor.state.value.flowPct
                                            ?: snap.dieselIafCmdPct
                                        )?.toDouble(),
                                    "diesel_iaf" to
                                        com.veplayer.app.vehicle.DieselIntakeAirflow.toJsonMap(
                                            com.veplayer.app.vehicle.DieselIntakeAirflowMonitor.state.value,
                                        ),
                                    "thr_act_warn_pct" to prefs.thrActWarnPct.toDouble(),
                                    "thr_act_alert_pct" to prefs.thrActAlertPct.toDouble(),
                                    "thr_act_speed_min_kmh" to prefs.thrActSpeedMinKmh.toDouble(),
                                    "thr_actuator_pct" to
                                        (if (prefs.thrActSimPct > 0f) prefs.thrActSimPct
                                        else com.veplayer.app.vehicle.ThrottleActuatorMonitor.state.value.actuatorPct
                                            ?: snap.thrActuatorPct
                                        )?.toDouble(),
                                    "thr_act" to
                                        com.veplayer.app.vehicle.ThrottleActuator.toJsonMap(
                                            com.veplayer.app.vehicle.ThrottleActuatorMonitor.state.value,
                                        ),
                                    "cat_b1s11_warn_c" to prefs.catB1s11WarnC.toDouble(),
                                    "cat_b1s11_alert_c" to prefs.catB1s11AlertC.toDouble(),
                                    "catalyst_b1s11_temp_c" to
                                        (if (prefs.catB1s11SimC > 0f) prefs.catB1s11SimC
                                        else com.veplayer.app.vehicle.CatalystB1S11Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s11TempC
                                        )?.toDouble(),
                                    "catalyst_b1s11" to
                                        com.veplayer.app.vehicle.CatalystB1S11.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S11Monitor.state.value,
                                        ),
                                    "cat_b2s11_warn_c" to prefs.catB2s11WarnC.toDouble(),
                                    "cat_b2s11_alert_c" to prefs.catB2s11AlertC.toDouble(),
                                    "catalyst_b2s11_temp_c" to
                                        (if (prefs.catB2s11SimC > 0f) prefs.catB2s11SimC
                                        else com.veplayer.app.vehicle.CatalystB2S11Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s11TempC
                                        )?.toDouble(),
                                    "catalyst_b2s11" to
                                        com.veplayer.app.vehicle.CatalystB2S11.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S11Monitor.state.value,
                                        ),
                                    "egr_actual_warn_pct" to prefs.egrActualWarnPct.toDouble(),
                                    "egr_actual_alert_pct" to prefs.egrActualAlertPct.toDouble(),
                                    "egr_actual_speed_min_kmh" to prefs.egrActualSpeedMinKmh.toDouble(),
                                    "actual_egr_pct" to
                                        (if (prefs.egrActualSimPct > 0f) prefs.egrActualSimPct
                                        else com.veplayer.app.vehicle.ActualEgrMonitor.state.value.egrPct
                                            ?: snap.actualEgrPct
                                        )?.toDouble(),
                                    "egr_actual" to
                                        com.veplayer.app.vehicle.ActualEgr.toJsonMap(
                                            com.veplayer.app.vehicle.ActualEgrMonitor.state.value,
                                        ),
                                    "inject_ctrl_warn_kpa" to prefs.injectCtrlWarnKpa.toDouble(),
                                    "inject_ctrl_alert_kpa" to prefs.injectCtrlAlertKpa.toDouble(),
                                    "inject_ctrl_speed_min_kmh" to prefs.injectCtrlSpeedMinKmh.toDouble(),
                                    "inject_ctrl_kpa" to
                                        (if (prefs.injectCtrlSimKpa > 0f) prefs.injectCtrlSimKpa
                                        else com.veplayer.app.vehicle.InjectPressureControlMonitor.state.value.pressureKpa
                                            ?: snap.injectCtrlKpa
                                        )?.toDouble(),
                                    "inject_ctrl" to
                                        com.veplayer.app.vehicle.InjectPressureControl.toJsonMap(
                                            com.veplayer.app.vehicle.InjectPressureControlMonitor.state.value,
                                        ),
                                    "fuel_ctrl_warn_kpa" to prefs.fuelCtrlWarnKpa.toDouble(),
                                    "fuel_ctrl_alert_kpa" to prefs.fuelCtrlAlertKpa.toDouble(),
                                    "fuel_ctrl_speed_min_kmh" to prefs.fuelCtrlSpeedMinKmh.toDouble(),
                                    "fuel_ctrl_kpa" to
                                        (if (prefs.fuelCtrlSimKpa > 0f) prefs.fuelCtrlSimKpa
                                        else com.veplayer.app.vehicle.FuelPressureControlMonitor.state.value.pressureKpa
                                            ?: snap.fuelCtrlKpa
                                        )?.toDouble(),
                                    "fuel_ctrl" to
                                        com.veplayer.app.vehicle.FuelPressureControl.toJsonMap(
                                            com.veplayer.app.vehicle.FuelPressureControlMonitor.state.value,
                                        ),
                                    "cat_b1s12_warn_c" to prefs.catB1s12WarnC.toDouble(),
                                    "cat_b1s12_alert_c" to prefs.catB1s12AlertC.toDouble(),
                                    "catalyst_b1s12_temp_c" to
                                        (if (prefs.catB1s12SimC > 0f) prefs.catB1s12SimC
                                        else com.veplayer.app.vehicle.CatalystB1S12Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s12TempC
                                        )?.toDouble(),
                                    "catalyst_b1s12" to
                                        com.veplayer.app.vehicle.CatalystB1S12.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S12Monitor.state.value,
                                        ),
                                    "cat_b2s12_warn_c" to prefs.catB2s12WarnC.toDouble(),
                                    "cat_b2s12_alert_c" to prefs.catB2s12AlertC.toDouble(),
                                    "catalyst_b2s12_temp_c" to
                                        (if (prefs.catB2s12SimC > 0f) prefs.catB2s12SimC
                                        else com.veplayer.app.vehicle.CatalystB2S12Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s12TempC
                                        )?.toDouble(),
                                    "catalyst_b2s12" to
                                        com.veplayer.app.vehicle.CatalystB2S12.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S12Monitor.state.value,
                                        ),
                                    "stft_b2_warn_pct" to prefs.stftB2WarnPct.toDouble(),
                                    "stft_b2_alert_pct" to prefs.stftB2AlertPct.toDouble(),
                                    "stft_b2_speed_min_kmh" to prefs.stftB2SpeedMinKmh.toDouble(),
                                    "fuel_trim_stft_b2_pct" to
                                        (if (prefs.stftB2SimPct != 0f) prefs.stftB2SimPct
                                        else com.veplayer.app.vehicle.FuelTrimStftB2Monitor.state.value.trimPct
                                            ?: snap.fuelTrimStftB2Pct
                                        )?.toDouble(),
                                    "stft_b2" to
                                        com.veplayer.app.vehicle.FuelTrimStftB2.toJsonMap(
                                            com.veplayer.app.vehicle.FuelTrimStftB2Monitor.state.value,
                                        ),
                                    "ltft_b2_warn_pct" to prefs.ltftB2WarnPct.toDouble(),
                                    "ltft_b2_alert_pct" to prefs.ltftB2AlertPct.toDouble(),
                                    "ltft_b2_speed_min_kmh" to prefs.ltftB2SpeedMinKmh.toDouble(),
                                    "fuel_trim_ltft_b2_pct" to
                                        (if (prefs.ltftB2SimPct != 0f) prefs.ltftB2SimPct
                                        else com.veplayer.app.vehicle.FuelTrimLtftB2Monitor.state.value.trimPct
                                            ?: snap.fuelTrimLtftB2Pct
                                        )?.toDouble(),
                                    "ltft_b2" to
                                        com.veplayer.app.vehicle.FuelTrimLtftB2.toJsonMap(
                                            com.veplayer.app.vehicle.FuelTrimLtftB2Monitor.state.value,
                                        ),
                                    "cat_b1s13_warn_c" to prefs.catB1s13WarnC.toDouble(),
                                    "cat_b1s13_alert_c" to prefs.catB1s13AlertC.toDouble(),
                                    "catalyst_b1s13_temp_c" to
                                        (if (prefs.catB1s13SimC > 0f) prefs.catB1s13SimC
                                        else com.veplayer.app.vehicle.CatalystB1S13Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s13TempC
                                        )?.toDouble(),
                                    "catalyst_b1s13" to
                                        com.veplayer.app.vehicle.CatalystB1S13.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S13Monitor.state.value,
                                        ),
                                    "cat_b2s13_warn_c" to prefs.catB2s13WarnC.toDouble(),
                                    "cat_b2s13_alert_c" to prefs.catB2s13AlertC.toDouble(),
                                    "catalyst_b2s13_temp_c" to
                                        (if (prefs.catB2s13SimC > 0f) prefs.catB2s13SimC
                                        else com.veplayer.app.vehicle.CatalystB2S13Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s13TempC
                                        )?.toDouble(),
                                    "catalyst_b2s13" to
                                        com.veplayer.app.vehicle.CatalystB2S13.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S13Monitor.state.value,
                                        ),
                                    "dpf_trigger_warn_pct" to prefs.dpfTrigWarnPct.toDouble(),
                                    "dpf_trigger_alert_pct" to prefs.dpfTrigAlertPct.toDouble(),
                                    "dpf_trigger_speed_min_kmh" to prefs.dpfTrigSpeedMinKmh.toDouble(),
                                    "dpf_trigger_pct" to
                                        (if (prefs.dpfTrigSimPct > 0f) prefs.dpfTrigSimPct
                                        else com.veplayer.app.vehicle.DpfAftertreatmentMonitor.state.value.triggerPct
                                            ?: snap.dpfTriggerPct
                                        )?.toDouble(),
                                    "dpf_aftertreatment" to
                                        com.veplayer.app.vehicle.DpfAftertreatment.toJsonMap(
                                            com.veplayer.app.vehicle.DpfAftertreatmentMonitor.state.value,
                                        ),
                                    "thr_g_warn_pct" to prefs.thrGWarnPct.toDouble(),
                                    "thr_g_alert_pct" to prefs.thrGAlertPct.toDouble(),
                                    "thr_g_speed_min_kmh" to prefs.thrGSpeedMinKmh.toDouble(),
                                    "throttle_g_pct" to
                                        (if (prefs.thrGSimPct > 0f) prefs.thrGSimPct
                                        else com.veplayer.app.vehicle.ThrottleGMonitor.state.value.throttlePct
                                            ?: snap.throttleGPct
                                        )?.toDouble(),
                                    "throttle_g" to
                                        com.veplayer.app.vehicle.ThrottleG.toJsonMap(
                                            com.veplayer.app.vehicle.ThrottleGMonitor.state.value,
                                        ),
                                    "eng_friction_warn_pct" to prefs.engFrictionWarnPct.toDouble(),
                                    "eng_friction_alert_pct" to prefs.engFrictionAlertPct.toDouble(),
                                    "eng_friction_speed_min_kmh" to prefs.engFrictionSpeedMinKmh.toDouble(),
                                    "engine_friction_pct" to
                                        (if (prefs.engFrictionSimPct != 0f) prefs.engFrictionSimPct
                                        else com.veplayer.app.vehicle.EngineFrictionTorqueMonitor.state.value.frictionPct
                                            ?: snap.engineFrictionPct
                                        )?.toDouble(),
                                    "eng_friction" to
                                        com.veplayer.app.vehicle.EngineFrictionTorque.toJsonMap(
                                            com.veplayer.app.vehicle.EngineFrictionTorqueMonitor.state.value,
                                        ),
                                    "mil_dist_warn_km" to prefs.milDistWarnKm.toDouble(),
                                    "mil_dist_alert_km" to prefs.milDistAlertKm.toDouble(),
                                    "mil_distance_km" to
                                        (if (prefs.milDistSimKm > 0f) prefs.milDistSimKm
                                        else
                                            com.veplayer.app.vehicle.MilDistanceMonitor.state.value.distanceKm
                                                ?: snap.milDistanceKm
                                        )?.toDouble(),
                                    "mil_dist" to
                                        com.veplayer.app.vehicle.MilDistance.toJsonMap(
                                            com.veplayer.app.vehicle.MilDistanceMonitor.state.value,
                                        ),
                                    "dist_clear_warn_km" to prefs.distClearWarnKm.toDouble(),
                                    "dist_clear_alert_km" to prefs.distClearAlertKm.toDouble(),
                                    "dist_since_clear_km" to
                                        (if (prefs.distClearSimKm > 0f) prefs.distClearSimKm
                                        else
                                            com.veplayer.app.vehicle.DistSinceClearMonitor.state.value.distanceKm
                                                ?: snap.distSinceClearKm
                                        )?.toDouble(),
                                    "dist_since_clear" to
                                        com.veplayer.app.vehicle.DistSinceClear.toJsonMap(
                                            com.veplayer.app.vehicle.DistSinceClearMonitor.state.value,
                                        ),
                                    "rpm_warn" to prefs.rpmWarn.toDouble(),
                                    "rpm_alert" to prefs.rpmAlert.toDouble(),
                                    "rpm" to
                                        (if (prefs.rpmSim > 0f) prefs.rpmSim
                                        else com.veplayer.app.vehicle.RpmOverRevMonitor.state.value.rpm
                                            ?: snap.rpm
                                        )?.toDouble(),
                                    "engine_load_pct" to
                                        (if (prefs.engineLoadSimPct > 0f) prefs.engineLoadSimPct
                                        else com.veplayer.app.vehicle.EngineLoadMonitor.state.value.loadPct
                                            ?: snap.engineLoadPct
                                        )?.toDouble(),
                                    "engine_load_warn_pct" to prefs.engineLoadWarnPct.toDouble(),
                                    "engine_load_alert_pct" to prefs.engineLoadAlertPct.toDouble(),
                                    "engine_load_speed_min_kmh" to prefs.engineLoadSpeedMinKmh.toDouble(),
                                    "engine_load" to
                                        com.veplayer.app.vehicle.EngineLoad.toJsonMap(
                                            com.veplayer.app.vehicle.EngineLoadMonitor.state.value,
                                        ),
                                    "fuel_trim_stft_pct" to
                                        (if (prefs.stftSimPct != 0f) prefs.stftSimPct
                                        else com.veplayer.app.vehicle.FuelTrimStftMonitor.state.value.trimPct
                                            ?: snap.fuelTrimStftPct
                                        )?.toDouble(),
                                    "stft_warn_pct" to prefs.stftWarnPct.toDouble(),
                                    "stft_alert_pct" to prefs.stftAlertPct.toDouble(),
                                    "stft_speed_min_kmh" to prefs.stftSpeedMinKmh.toDouble(),
                                    "fuel_trim_stft" to
                                        com.veplayer.app.vehicle.FuelTrimStft.toJsonMap(
                                            com.veplayer.app.vehicle.FuelTrimStftMonitor.state.value,
                                        ),
                                    "fuel_trim_ltft_pct" to
                                        (if (prefs.ltftSimPct != 0f) prefs.ltftSimPct
                                        else com.veplayer.app.vehicle.FuelTrimLtftMonitor.state.value.trimPct
                                            ?: snap.fuelTrimLtftPct
                                        )?.toDouble(),
                                    "ltft_warn_pct" to prefs.ltftWarnPct.toDouble(),
                                    "ltft_alert_pct" to prefs.ltftAlertPct.toDouble(),
                                    "ltft_speed_min_kmh" to prefs.ltftSpeedMinKmh.toDouble(),
                                    "fuel_trim_ltft" to
                                        com.veplayer.app.vehicle.FuelTrimLtft.toJsonMap(
                                            com.veplayer.app.vehicle.FuelTrimLtftMonitor.state.value,
                                        ),
                                    "map_kpa" to
                                        (if (prefs.mapSimKpa > 0f) prefs.mapSimKpa
                                        else com.veplayer.app.vehicle.MapPressureMonitor.state.value.mapKpa
                                            ?: snap.mapKpa
                                        )?.toDouble(),
                                    "map_warn_kpa" to prefs.mapWarnKpa.toDouble(),
                                    "map_alert_kpa" to prefs.mapAlertKpa.toDouble(),
                                    "map_speed_min_kmh" to prefs.mapSpeedMinKmh.toDouble(),
                                    "map_pressure" to
                                        com.veplayer.app.vehicle.MapPressure.toJsonMap(
                                            com.veplayer.app.vehicle.MapPressureMonitor.state.value,
                                        ),
                                    "throttle_pct" to
                                        (if (prefs.throttleSimPct > 0f) prefs.throttleSimPct
                                        else com.veplayer.app.vehicle.HighThrottleMonitor.state.value.throttlePct
                                            ?: snap.throttlePct
                                        )?.toDouble(),
                                    "throttle_warn_pct" to prefs.throttleWarnPct.toDouble(),
                                    "throttle_alert_pct" to prefs.throttleAlertPct.toDouble(),
                                    "throttle_alert_hold_sec" to prefs.throttleAlertHoldSec.toDouble(),
                                    "throttle_high_sec" to
                                        com.veplayer.app.vehicle.HighThrottleMonitor.state.value.highForSec.toDouble(),
                                    "throttle" to
                                        com.veplayer.app.vehicle.HighThrottle.toJsonMap(
                                            com.veplayer.app.vehicle.HighThrottleMonitor.state.value,
                                        ),
                                    "tow_moving_sec" to
                                        com.veplayer.app.vehicle.UnauthorizedMoveMonitor.state.value.movingForSec.toDouble(),
                                    "tow_speed_min_kmh" to prefs.towSpeedMinKmh.toDouble(),
                                    "tow_warn_sec" to prefs.towWarnSec.toDouble(),
                                    "tow_alert_sec" to prefs.towAlertSec.toDouble(),
                                    "pbrake_warn_kmh" to prefs.pbrakeWarnKmh.toDouble(),
                                    "pbrake_alert_kmh" to prefs.pbrakeAlertKmh.toDouble(),
                                    "parking_brake" to
                                        (prefs.pbrakeSim ||
                                            com.veplayer.app.vehicle.ParkingBrakeMovingMonitor.state.value.parkingBrake ||
                                            snap.parkingBrake),
                                    "gear_roll_warn_kmh" to prefs.gearRollWarnKmh.toDouble(),
                                    "gear_roll_alert_kmh" to prefs.gearRollAlertKmh.toDouble(),
                                    "gear" to
                                        (if (prefs.gearRollSim) prefs.gearRollSimGear
                                        else com.veplayer.app.vehicle.GearRollMonitor.state.value.gear.ifBlank {
                                            snap.gear.name
                                        }),
                                    "speed_kmh" to
                                        (when {
                                            prefs.pbrakeSim && prefs.pbrakeSimKmh > 0f -> prefs.pbrakeSimKmh
                                            prefs.gearRollSim && prefs.gearRollSimKmh > 0f -> prefs.gearRollSimKmh
                                            else -> snap.speedKmh
                                        }).toDouble(),
                                    "turn_stuck_sec" to
                                        com.veplayer.app.vehicle.TurnStuckMonitor.state.value.heldSec.toDouble(),
                                    "turn_stuck_side" to
                                        com.veplayer.app.vehicle.TurnStuckMonitor.state.value.side.ifBlank { null },
                                    "turn_stuck_warn_sec" to prefs.turnStuckWarnSec.toDouble(),
                                    "turn_stuck_alert_sec" to prefs.turnStuckAlertSec.toDouble(),
                                    "hazard_stuck_sec" to
                                        com.veplayer.app.vehicle.HazardStuckMonitor.state.value.heldSec.toDouble(),
                                    "hazard_stuck_warn_sec" to prefs.hazardStuckWarnSec.toDouble(),
                                    "hazard_stuck_alert_sec" to prefs.hazardStuckAlertSec.toDouble(),
                                    "fuel_drop_pct" to
                                        com.veplayer.app.vehicle.SuddenFuelDropMonitor.state.value.dropPct.toDouble(),
                                    "fuel_drop_warn_pct" to prefs.fuelDropWarnPct.toDouble(),
                                    "fuel_drop_alert_pct" to prefs.fuelDropAlertPct.toDouble(),
                                    "battery_warn_v" to prefs.battVoltWarnV.toDouble(),
                                    "battery_alert_v" to prefs.battVoltAlertV.toDouble(),
                                    "battery_voltage_v" to
                                        (if (prefs.battVoltSimV > 0f) prefs.battVoltSimV
                                        else com.veplayer.app.vehicle.BatteryVoltageMonitor.state.value.volts
                                            ?: snap.batteryVoltageV
                                        )?.toDouble(),
                                    "tpms_warn_psi" to prefs.tpmsWarnPsi.toDouble(),
                                    "tpms_alert_psi" to prefs.tpmsAlertPsi.toDouble(),
                                    "tpms" to
                                        run {
                                            val st = com.veplayer.app.vehicle.TpmsHudMonitor.state.value
                                            if (st.wheels.isNotEmpty()) {
                                                com.veplayer.app.vehicle.TpmsHud.toJsonMap(st)
                                            } else {
                                                snap.toJsonMap()["tpms"]
                                            }
                                        },
                                    "harsh" to com.veplayer.app.vehicle.HarshDriving.toJsonMap(
                                        com.veplayer.app.vehicle.HarshDrivingMonitor.state.value,
                                    ),
                                    "impact" to com.veplayer.app.vehicle.ImpactDetect.toJsonMap(
                                        com.veplayer.app.vehicle.ImpactDetectMonitor.state.value,
                                    ),
                                    "abs" to com.veplayer.app.vehicle.AbsHud.toJsonMap(
                                        com.veplayer.app.vehicle.AbsHudMonitor.state.value,
                                    ),
                                    "abs_active" to
                                        (prefs.absSim ||
                                            com.veplayer.app.vehicle.AbsHudMonitor.state.value.active ||
                                            snap.absActive),
                                    "abs_active_sec" to
                                        com.veplayer.app.vehicle.AbsHudMonitor.state.value.activeForSec.toDouble(),
                                    "abs_events" to
                                        com.veplayer.app.vehicle.AbsHudMonitor.state.value.events,
                                    "abs_warn_sec" to prefs.absWarnSec.toDouble(),
                                    "abs_alert_sec" to prefs.absAlertSec.toDouble(),
                                    "abs_alert_events" to prefs.absAlertEvents.toDouble(),
                                    "speed_limit_kmh" to
                                        com.veplayer.app.vehicle.SpeedHudMonitor.effectiveLimitKmh(prefs),
                                            "phone_link" to com.veplayer.app.phone.PhoneLinkBus.state.value.toJsonMap(),
                                            "brand" to com.veplayer.app.brand.BrandRepository.toJsonMap(prefs),
                                            "kiosk" to com.veplayer.app.kiosk.KioskController.healthSnapshot(this@SenseBridgeService),
                                            "field" to
                                                mapOf(
                                                    "package" to packageName,
                                                    "cams" to com.veplayer.app.camera.CameraCatalog.list(this@SenseBridgeService).size,
                                                    "signal_source" to prefs.signalSource,
                                                    "can" to com.veplayer.app.vehicle.can.CanLinkBus.state.value.state.name,
                                                    "obd" to com.veplayer.app.vehicle.ObdLinkBus.state.value.state.name,
                                                ),
                                        ),
                            ).getOrThrow()
                        remote.handle(hb.commands)
                        remote.handleAlerts(hb.alerts)
                        com.veplayer.app.fleet.PanicBus.applyFromHeartbeat(
                            hb.panicOpen,
                            hb.panicAlertId,
                            hb.panicMessage,
                            hb.panicClipUrl,
                        )
                        if (hb.speedZoneId != null && hb.speedZoneMaxKmh != null) {
                            com.veplayer.app.vehicle.SpeedZoneBus.apply(
                                com.veplayer.app.vehicle.SpeedZoneBus.Zone(
                                    id = hb.speedZoneId,
                                    name = hb.speedZoneName ?: "Zona",
                                    maxKmh = hb.speedZoneMaxKmh,
                                ),
                            )
                        } else {
                            com.veplayer.app.vehicle.SpeedZoneBus.clear()
                        }
                        com.veplayer.app.fleet.ShiftTracker.tickLocal(prefs)
                        com.veplayer.app.fleet.ShiftTracker.applyFromHeartbeat(hb.shiftJson)
                        SilentOtaCoordinator.maybeApply(this@SenseBridgeService, hb.ota)
                    }
                    runCatching {
                        val actors =
                            surroundClient
                                .fetch(loc.latitude, loc.longitude, headingDeg = snap.headingDeg)
                                .getOrThrow()
                        SurroundEngine.publishSenseflow(actors)
                    }
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        prefs = VePrefs(this)
        fleet = FleetClient(prefs)
        remote = RemoteCommandExecutor(this, fleet)
        surroundClient = SenseflowSurroundClient(prefs)
        CanBusManager.start(this)
        startFg()
        scope.launch {
            runCatching {
                fleet.register()
                val snap = VehicleState.state.value
                val hb =
                    fleet.heartbeat(
                        speedMps = snap.speedMps,
                        reverse = snap.reverse,
                        vehicleSignals =
                            snap.toJsonMap() +
                                mapOf(
                                    "idle_sec" to com.veplayer.app.vehicle.IdleMonitor.state.value.idleForSec.toDouble(),
                                    "idle_band" to com.veplayer.app.vehicle.IdleMonitor.state.value.band,
                                    "shift_duration_sec" to
                                        com.veplayer.app.vehicle.ShiftFatigueMonitor.state.value.durationSec.toDouble(),
                                    "shift_band" to com.veplayer.app.vehicle.ShiftFatigueMonitor.state.value.band,
                                    "rest_drive_sec" to
                                        com.veplayer.app.vehicle.RestBreakMonitor.state.value.drivingSec.toDouble(),
                                    "rest_warn_sec" to (prefs.restDriveWarnMin * 60f).toDouble(),
                                    "rest_alert_sec" to (prefs.restDriveAlertMin * 60f).toDouble(),
                                    "route_off_m" to
                                        com.veplayer.app.vehicle.RouteDeviationMonitor.state.value.distanceM.toDouble(),
                                    "route_warn_m" to prefs.routeDevWarnM.toDouble(),
                                    "route_alert_m" to prefs.routeDevAlertM.toDouble(),
                                    "route_dev" to
                                        com.veplayer.app.vehicle.RouteDeviation.toJsonMap(
                                            com.veplayer.app.vehicle.RouteDeviationMonitor.state.value,
                                        ),
                                    "driver_score" to
                                        com.veplayer.app.vehicle.DriverScore.toJsonMap(
                                            com.veplayer.app.vehicle.DriverScoreMonitor.state.value,
                                        ),
                                    "driver_score_warn" to prefs.driverScoreWarn.toDouble(),
                                    "driver_score_alert" to prefs.driverScoreAlert.toDouble(),
                                    "eco_live" to
                                        com.veplayer.app.vehicle.EcoLive.toJsonMap(
                                            com.veplayer.app.vehicle.EcoLiveMonitor.state.value,
                                        ),
                                    "eco_score" to
                                        com.veplayer.app.vehicle.EcoLiveMonitor.state.value.score.takeIf {
                                            com.veplayer.app.vehicle.EcoLiveMonitor.state.value.active
                                        },
                                    "eco_band" to
                                        com.veplayer.app.vehicle.EcoLiveMonitor.state.value.band.takeIf {
                                            com.veplayer.app.vehicle.EcoLiveMonitor.state.value.active
                                        },
                                    "eco_warn_score" to prefs.ecoLiveWarn.toDouble(),
                                    "eco_alert_score" to prefs.ecoLiveAlert.toDouble(),
                                    "runtime_sec" to
                                        (if (prefs.engineRuntimeSimHours > 0f)
                                            (prefs.engineRuntimeSimHours * 3600f).toInt()
                                        else
                                            com.veplayer.app.vehicle.EngineRuntimeMonitor.state.value.runtimeSec
                                                ?: snap.runtimeSec
                                        ),
                                    "runtime_warn_sec" to (prefs.engineRuntimeWarnHours * 3600f).toDouble(),
                                    "runtime_alert_sec" to (prefs.engineRuntimeAlertHours * 3600f).toDouble(),
                                    "engine_runtime" to
                                        com.veplayer.app.vehicle.EngineRuntime.toJsonMap(
                                            com.veplayer.app.vehicle.EngineRuntimeMonitor.state.value,
                                        ),
                                    "cabin_warn_c" to prefs.cabinWarnC.toDouble(),
                                    "cabin_alert_c" to prefs.cabinAlertC.toDouble(),
                                    "ice_warn_c" to prefs.iceWarnC.toDouble(),
                                    "ice_alert_c" to prefs.iceAlertC.toDouble(),
                                    "outdoor_temp_c" to
                                        (if (prefs.iceSimOn) prefs.iceSimC
                                        else com.veplayer.app.vehicle.IceFrostMonitor.state.value.outdoorC
                                            ?: snap.outdoorTempC
                                        )?.toDouble(),
                                    "coolant_warn_c" to prefs.coolantWarnC.toDouble(),
                                    "coolant_alert_c" to prefs.coolantAlertC.toDouble(),
                                    "coolant_c" to
                                        (if (prefs.coolantSimC > 0f) prefs.coolantSimC
                                        else com.veplayer.app.vehicle.VehicleState.state.value.coolantC
                                        )?.toDouble(),
                                    "oil_temp_warn_c" to prefs.oilTempWarnC.toDouble(),
                                    "oil_temp_alert_c" to prefs.oilTempAlertC.toDouble(),
                                    "oil_temp_c" to
                                        (if (prefs.oilTempSimC > 0f) prefs.oilTempSimC
                                        else com.veplayer.app.vehicle.OilTempMonitor.state.value.oilTempC
                                            ?: snap.oilTempC
                                        )?.toDouble(),
                                    "oil_temp" to
                                        com.veplayer.app.vehicle.OilTemp.toJsonMap(
                                            com.veplayer.app.vehicle.OilTempMonitor.state.value,
                                        ),
                                    "catalyst_warn_c" to prefs.catalystWarnC.toDouble(),
                                    "catalyst_alert_c" to prefs.catalystAlertC.toDouble(),
                                    "catalyst_temp_c" to
                                        (if (prefs.catalystSimC > 0f) prefs.catalystSimC
                                        else com.veplayer.app.vehicle.CatalystTempMonitor.state.value.catalystTempC
                                            ?: snap.catalystTempC
                                        )?.toDouble(),
                                    "catalyst_temp" to
                                        com.veplayer.app.vehicle.CatalystTemp.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystTempMonitor.state.value,
                                        ),
                                    "intake_air_warn_c" to prefs.intakeAirWarnC.toDouble(),
                                    "intake_air_alert_c" to prefs.intakeAirAlertC.toDouble(),
                                    "intake_air_c" to
                                        (if (prefs.intakeAirSimC > 0f) prefs.intakeAirSimC
                                        else com.veplayer.app.vehicle.IntakeAirMonitor.state.value.intakeAirC
                                            ?: snap.intakeAirC
                                        )?.toDouble(),
                                    "intake_air" to
                                        com.veplayer.app.vehicle.IntakeAir.toJsonMap(
                                            com.veplayer.app.vehicle.IntakeAirMonitor.state.value,
                                        ),
                                    "fuel_rate_warn_lph" to prefs.fuelRateWarnLph.toDouble(),
                                    "fuel_rate_alert_lph" to prefs.fuelRateAlertLph.toDouble(),
                                    "fuel_rate_speed_min_kmh" to prefs.fuelRateSpeedMinKmh.toDouble(),
                                    "fuel_rate_gps" to
                                        (if (prefs.fuelRateSimLph > 0f)
                                            com.veplayer.app.vehicle.FuelRate.lphToGps(prefs.fuelRateSimLph)
                                        else
                                            com.veplayer.app.vehicle.FuelRateMonitor.state.value.fuelRateGps
                                                ?: snap.fuelRateGps
                                        )?.toDouble(),
                                    "fuel_rate_lph" to
                                        com.veplayer.app.vehicle.FuelRateMonitor.state.value.fuelRateLph?.toDouble(),
                                    "fuel_rate" to
                                        com.veplayer.app.vehicle.FuelRate.toJsonMap(
                                            com.veplayer.app.vehicle.FuelRateMonitor.state.value,
                                        ),
                                    "maf_warn_gps" to prefs.mafWarnGps.toDouble(),
                                    "maf_alert_gps" to prefs.mafAlertGps.toDouble(),
                                    "maf_speed_min_kmh" to prefs.mafSpeedMinKmh.toDouble(),
                                    "maf_gps" to
                                        (if (prefs.mafSimGps > 0f) prefs.mafSimGps
                                        else com.veplayer.app.vehicle.MafAirflowMonitor.state.value.mafGps
                                            ?: snap.mafGps
                                        )?.toDouble(),
                                    "maf_airflow" to
                                        com.veplayer.app.vehicle.MafAirflow.toJsonMap(
                                            com.veplayer.app.vehicle.MafAirflowMonitor.state.value,
                                        ),
                                    "fuel_press_warn_kpa" to prefs.fuelPressWarnKpa.toDouble(),
                                    "fuel_press_alert_kpa" to prefs.fuelPressAlertKpa.toDouble(),
                                    "fuel_press_speed_min_kmh" to prefs.fuelPressSpeedMinKmh.toDouble(),
                                    "fuel_pressure_kpa" to
                                        (if (prefs.fuelPressSimKpa > 0f) prefs.fuelPressSimKpa
                                        else com.veplayer.app.vehicle.FuelPressureMonitor.state.value.pressureKpa
                                            ?: snap.fuelPressureKpa
                                        )?.toDouble(),
                                    "fuel_pressure" to
                                        com.veplayer.app.vehicle.FuelPressure.toJsonMap(
                                            com.veplayer.app.vehicle.FuelPressureMonitor.state.value,
                                        ),
                                    "baro_warn_low_kpa" to prefs.baroWarnLowKpa.toDouble(),
                                    "baro_alert_low_kpa" to prefs.baroAlertLowKpa.toDouble(),
                                    "baro_warn_high_kpa" to prefs.baroWarnHighKpa.toDouble(),
                                    "baro_alert_high_kpa" to prefs.baroAlertHighKpa.toDouble(),
                                    "baro_speed_min_kmh" to prefs.baroSpeedMinKmh.toDouble(),
                                    "baro_kpa" to
                                        (if (prefs.baroSimKpa > 0f) prefs.baroSimKpa
                                        else com.veplayer.app.vehicle.BarometricPressureMonitor.state.value.baroKpa
                                            ?: snap.baroKpa
                                        )?.toDouble(),
                                    "barometric" to
                                        com.veplayer.app.vehicle.BarometricPressure.toJsonMap(
                                            com.veplayer.app.vehicle.BarometricPressureMonitor.state.value,
                                        ),
                                    "timing_warn_deg" to prefs.timingWarnDeg.toDouble(),
                                    "timing_alert_deg" to prefs.timingAlertDeg.toDouble(),
                                    "timing_speed_min_kmh" to prefs.timingSpeedMinKmh.toDouble(),
                                    "timing_rpm_min" to prefs.timingRpmMin.toDouble(),
                                    "timing_advance_deg" to
                                        (if (prefs.timingSimDeg != 0f) prefs.timingSimDeg
                                        else com.veplayer.app.vehicle.TimingAdvanceMonitor.state.value.timingDeg
                                            ?: snap.timingAdvanceDeg
                                        )?.toDouble(),
                                    "timing_advance" to
                                        com.veplayer.app.vehicle.TimingAdvance.toJsonMap(
                                            com.veplayer.app.vehicle.TimingAdvanceMonitor.state.value,
                                        ),
                                    "o2_warn_low_v" to prefs.o2WarnLowV.toDouble(),
                                    "o2_alert_low_v" to prefs.o2AlertLowV.toDouble(),
                                    "o2_warn_high_v" to prefs.o2WarnHighV.toDouble(),
                                    "o2_alert_high_v" to prefs.o2AlertHighV.toDouble(),
                                    "o2_speed_min_kmh" to prefs.o2SpeedMinKmh.toDouble(),
                                    "o2_rpm_min" to prefs.o2RpmMin.toDouble(),
                                    "o2_b1s1_volts" to
                                        (if (prefs.o2SimVolts > 0f) prefs.o2SimVolts
                                        else com.veplayer.app.vehicle.O2VoltageMonitor.state.value.o2Volts
                                            ?: snap.o2B1s1Volts
                                        )?.toDouble(),
                                    "o2_voltage" to
                                        com.veplayer.app.vehicle.O2Voltage.toJsonMap(
                                            com.veplayer.app.vehicle.O2VoltageMonitor.state.value,
                                        ),
                                    "abs_load_warn_pct" to prefs.absLoadWarnPct.toDouble(),
                                    "abs_load_alert_pct" to prefs.absLoadAlertPct.toDouble(),
                                    "abs_load_speed_min_kmh" to prefs.absLoadSpeedMinKmh.toDouble(),
                                    "absolute_load_pct" to
                                        (if (prefs.absLoadSimPct > 0f) prefs.absLoadSimPct
                                        else com.veplayer.app.vehicle.AbsoluteLoadMonitor.state.value.loadPct
                                            ?: snap.absoluteLoadPct
                                        )?.toDouble(),
                                    "absolute_load" to
                                        com.veplayer.app.vehicle.AbsoluteLoad.toJsonMap(
                                            com.veplayer.app.vehicle.AbsoluteLoadMonitor.state.value,
                                        ),
                                    "rel_thr_warn_pct" to prefs.relThrWarnPct.toDouble(),
                                    "rel_thr_alert_pct" to prefs.relThrAlertPct.toDouble(),
                                    "rel_thr_speed_min_kmh" to prefs.relThrSpeedMinKmh.toDouble(),
                                    "relative_throttle_pct" to
                                        (if (prefs.relThrSimPct > 0f) prefs.relThrSimPct
                                        else com.veplayer.app.vehicle.RelativeThrottleMonitor.state.value.throttlePct
                                            ?: snap.relativeThrottlePct
                                        )?.toDouble(),
                                    "relative_throttle" to
                                        com.veplayer.app.vehicle.RelativeThrottle.toJsonMap(
                                            com.veplayer.app.vehicle.RelativeThrottleMonitor.state.value,
                                        ),
                                    "accel_pedal_warn_pct" to prefs.accelPedalWarnPct.toDouble(),
                                    "accel_pedal_alert_pct" to prefs.accelPedalAlertPct.toDouble(),
                                    "accel_pedal_speed_min_kmh" to prefs.accelPedalSpeedMinKmh.toDouble(),
                                    "accel_pedal_pct" to
                                        (if (prefs.accelPedalSimPct > 0f) prefs.accelPedalSimPct
                                        else com.veplayer.app.vehicle.AccelPedalMonitor.state.value.pedalPct
                                            ?: snap.accelPedalPct
                                        )?.toDouble(),
                                    "accel_pedal" to
                                        com.veplayer.app.vehicle.AccelPedal.toJsonMap(
                                            com.veplayer.app.vehicle.AccelPedalMonitor.state.value,
                                        ),
                                    "o2_b2_warn_low_v" to prefs.o2B2WarnLowV.toDouble(),
                                    "o2_b2_alert_low_v" to prefs.o2B2AlertLowV.toDouble(),
                                    "o2_b2_warn_high_v" to prefs.o2B2WarnHighV.toDouble(),
                                    "o2_b2_alert_high_v" to prefs.o2B2AlertHighV.toDouble(),
                                    "o2_b2_speed_min_kmh" to prefs.o2B2SpeedMinKmh.toDouble(),
                                    "o2_b2_rpm_min" to prefs.o2B2RpmMin.toDouble(),
                                    "o2_b1s2_volts" to
                                        (if (prefs.o2B2SimVolts > 0f) prefs.o2B2SimVolts
                                        else com.veplayer.app.vehicle.O2B2VoltageMonitor.state.value.o2Volts
                                            ?: snap.o2B1s2Volts
                                        )?.toDouble(),
                                    "o2_b2_voltage" to
                                        com.veplayer.app.vehicle.O2B2Voltage.toJsonMap(
                                            com.veplayer.app.vehicle.O2B2VoltageMonitor.state.value,
                                        ),
                                    "egr_warn_pct" to prefs.egrWarnPct.toDouble(),
                                    "egr_alert_pct" to prefs.egrAlertPct.toDouble(),
                                    "egr_speed_min_kmh" to prefs.egrSpeedMinKmh.toDouble(),
                                    "egr_error_pct" to
                                        (if (prefs.egrSimPct != 0f) prefs.egrSimPct
                                        else com.veplayer.app.vehicle.EgrErrorMonitor.state.value.errorPct
                                            ?: snap.egrErrorPct
                                        )?.toDouble(),
                                    "egr_error" to
                                        com.veplayer.app.vehicle.EgrError.toJsonMap(
                                            com.veplayer.app.vehicle.EgrErrorMonitor.state.value,
                                        ),
                                    "equiv_warn_low" to prefs.equivWarnLow.toDouble(),
                                    "equiv_alert_low" to prefs.equivAlertLow.toDouble(),
                                    "equiv_warn_high" to prefs.equivWarnHigh.toDouble(),
                                    "equiv_alert_high" to prefs.equivAlertHigh.toDouble(),
                                    "equiv_speed_min_kmh" to prefs.equivSpeedMinKmh.toDouble(),
                                    "equiv_rpm_min" to prefs.equivRpmMin.toDouble(),
                                    "equiv_ratio" to
                                        (if (prefs.equivSimRatio > 0f) prefs.equivSimRatio
                                        else com.veplayer.app.vehicle.EquivRatioMonitor.state.value.ratio
                                            ?: snap.equivRatio
                                        )?.toDouble(),
                                    "equiv_ratio_state" to
                                        com.veplayer.app.vehicle.EquivRatio.toJsonMap(
                                            com.veplayer.app.vehicle.EquivRatioMonitor.state.value,
                                        ),
                                    "evap_purge_warn_pct" to prefs.evapPurgeWarnPct.toDouble(),
                                    "evap_purge_alert_pct" to prefs.evapPurgeAlertPct.toDouble(),
                                    "evap_purge_speed_min_kmh" to prefs.evapPurgeSpeedMinKmh.toDouble(),
                                    "evap_purge_pct" to
                                        (if (prefs.evapPurgeSimPct > 0f) prefs.evapPurgeSimPct
                                        else com.veplayer.app.vehicle.EvapPurgeMonitor.state.value.purgePct
                                            ?: snap.evapPurgePct
                                        )?.toDouble(),
                                    "evap_purge" to
                                        com.veplayer.app.vehicle.EvapPurge.toJsonMap(
                                            com.veplayer.app.vehicle.EvapPurgeMonitor.state.value,
                                        ),
                                    "ethanol_warn_pct" to prefs.ethanolWarnPct.toDouble(),
                                    "ethanol_alert_pct" to prefs.ethanolAlertPct.toDouble(),
                                    "ethanol_speed_min_kmh" to prefs.ethanolSpeedMinKmh.toDouble(),
                                    "ethanol_pct" to
                                        (if (prefs.ethanolSimPct > 0f) prefs.ethanolSimPct
                                        else com.veplayer.app.vehicle.EthanolPctMonitor.state.value.ethanolPct
                                            ?: snap.ethanolPct
                                        )?.toDouble(),
                                    "ethanol" to
                                        com.veplayer.app.vehicle.EthanolPct.toJsonMap(
                                            com.veplayer.app.vehicle.EthanolPctMonitor.state.value,
                                        ),
                                    "evap_vapor_warn_pa" to prefs.evapVaporWarnPa.toDouble(),
                                    "evap_vapor_alert_pa" to prefs.evapVaporAlertPa.toDouble(),
                                    "evap_vapor_speed_min_kmh" to prefs.evapVaporSpeedMinKmh.toDouble(),
                                    "evap_vapor_pa" to
                                        (if (prefs.evapVaporSimPa != 0f) prefs.evapVaporSimPa
                                        else com.veplayer.app.vehicle.EvapVaporMonitor.state.value.pressurePa
                                            ?: snap.evapVaporPa
                                        )?.toDouble(),
                                    "evap_vapor" to
                                        com.veplayer.app.vehicle.EvapVapor.toJsonMap(
                                            com.veplayer.app.vehicle.EvapVaporMonitor.state.value,
                                        ),
                                    "rail_abs_warn_kpa" to prefs.railAbsWarnKpa.toDouble(),
                                    "rail_abs_alert_kpa" to prefs.railAbsAlertKpa.toDouble(),
                                    "rail_abs_speed_min_kmh" to prefs.railAbsSpeedMinKmh.toDouble(),
                                    "fuel_rail_abs_kpa" to
                                        (if (prefs.railAbsSimKpa > 0f) prefs.railAbsSimKpa
                                        else com.veplayer.app.vehicle.FuelRailAbsMonitor.state.value.pressureKpa
                                            ?: snap.fuelRailAbsKpa
                                        )?.toDouble(),
                                    "fuel_rail_abs" to
                                        com.veplayer.app.vehicle.FuelRailAbs.toJsonMap(
                                            com.veplayer.app.vehicle.FuelRailAbsMonitor.state.value,
                                        ),
                                    "egr_cmd_warn_pct" to prefs.egrCmdWarnPct.toDouble(),
                                    "egr_cmd_alert_pct" to prefs.egrCmdAlertPct.toDouble(),
                                    "egr_cmd_speed_min_kmh" to prefs.egrCmdSpeedMinKmh.toDouble(),
                                    "egr_cmd_pct" to
                                        (if (prefs.egrCmdSimPct > 0f) prefs.egrCmdSimPct
                                        else com.veplayer.app.vehicle.CommandedEgrMonitor.state.value.egrPct
                                            ?: snap.egrCmdPct
                                        )?.toDouble(),
                                    "egr_cmd" to
                                        com.veplayer.app.vehicle.CommandedEgr.toJsonMap(
                                            com.veplayer.app.vehicle.CommandedEgrMonitor.state.value,
                                        ),
                                    "rel_aped_warn_pct" to prefs.relApedWarnPct.toDouble(),
                                    "rel_aped_alert_pct" to prefs.relApedAlertPct.toDouble(),
                                    "rel_aped_speed_min_kmh" to prefs.relApedSpeedMinKmh.toDouble(),
                                    "rel_accel_pedal_pct" to
                                        (if (prefs.relApedSimPct > 0f) prefs.relApedSimPct
                                        else com.veplayer.app.vehicle.RelAccelPedalMonitor.state.value.pedalPct
                                            ?: snap.relAccelPedalPct
                                        )?.toDouble(),
                                    "rel_aped" to
                                        com.veplayer.app.vehicle.RelAccelPedal.toJsonMap(
                                            com.veplayer.app.vehicle.RelAccelPedalMonitor.state.value,
                                        ),
                                    "drv_torque_warn_pct" to prefs.drvTorqueWarnPct.toDouble(),
                                    "drv_torque_alert_pct" to prefs.drvTorqueAlertPct.toDouble(),
                                    "drv_torque_speed_min_kmh" to prefs.drvTorqueSpeedMinKmh.toDouble(),
                                    "driver_torque_pct" to
                                        (if (prefs.drvTorqueSimPct != 0f) prefs.drvTorqueSimPct
                                        else com.veplayer.app.vehicle.DriverTorqueMonitor.state.value.torquePct
                                            ?: snap.driverTorquePct
                                        )?.toDouble(),
                                    "drv_torque" to
                                        com.veplayer.app.vehicle.DriverTorque.toJsonMap(
                                            com.veplayer.app.vehicle.DriverTorqueMonitor.state.value,
                                        ),
                                    "act_torque_warn_pct" to prefs.actTorqueWarnPct.toDouble(),
                                    "act_torque_alert_pct" to prefs.actTorqueAlertPct.toDouble(),
                                    "act_torque_speed_min_kmh" to prefs.actTorqueSpeedMinKmh.toDouble(),
                                    "actual_torque_pct" to
                                        (if (prefs.actTorqueSimPct != 0f) prefs.actTorqueSimPct
                                        else com.veplayer.app.vehicle.ActualTorqueMonitor.state.value.torquePct
                                            ?: snap.actualTorquePct
                                        )?.toDouble(),
                                    "act_torque" to
                                        com.veplayer.app.vehicle.ActualTorque.toJsonMap(
                                            com.veplayer.app.vehicle.ActualTorqueMonitor.state.value,
                                        ),
                                    "cat_b2_warn_c" to prefs.catB2WarnC.toDouble(),
                                    "cat_b2_alert_c" to prefs.catB2AlertC.toDouble(),
                                    "catalyst_b2_temp_c" to
                                        (if (prefs.catB2SimC > 0f) prefs.catB2SimC
                                        else com.veplayer.app.vehicle.CatalystB2Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2TempC
                                        )?.toDouble(),
                                    "catalyst_b2" to
                                        com.veplayer.app.vehicle.CatalystB2.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2Monitor.state.value,
                                        ),
                                    "cat_b1s2_warn_c" to prefs.catB1s2WarnC.toDouble(),
                                    "cat_b1s2_alert_c" to prefs.catB1s2AlertC.toDouble(),
                                    "catalyst_b1s2_temp_c" to
                                        (if (prefs.catB1s2SimC > 0f) prefs.catB1s2SimC
                                        else com.veplayer.app.vehicle.CatalystB1S2Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s2TempC
                                        )?.toDouble(),
                                    "catalyst_b1s2" to
                                        com.veplayer.app.vehicle.CatalystB1S2.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S2Monitor.state.value,
                                        ),
                                    "cat_b2s2_warn_c" to prefs.catB2s2WarnC.toDouble(),
                                    "cat_b2s2_alert_c" to prefs.catB2s2AlertC.toDouble(),
                                    "catalyst_b2s2_temp_c" to
                                        (if (prefs.catB2s2SimC > 0f) prefs.catB2s2SimC
                                        else com.veplayer.app.vehicle.CatalystB2S2Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s2TempC
                                        )?.toDouble(),
                                    "catalyst_b2s2" to
                                        com.veplayer.app.vehicle.CatalystB2S2.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S2Monitor.state.value,
                                        ),
                                    "cat_b1s3_warn_c" to prefs.catB1s3WarnC.toDouble(),
                                    "cat_b1s3_alert_c" to prefs.catB1s3AlertC.toDouble(),
                                    "catalyst_b1s3_temp_c" to
                                        (if (prefs.catB1s3SimC > 0f) prefs.catB1s3SimC
                                        else com.veplayer.app.vehicle.CatalystB1S3Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s3TempC
                                        )?.toDouble(),
                                    "catalyst_b1s3" to
                                        com.veplayer.app.vehicle.CatalystB1S3.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S3Monitor.state.value,
                                        ),
                                    "cat_b2s3_warn_c" to prefs.catB2s3WarnC.toDouble(),
                                    "cat_b2s3_alert_c" to prefs.catB2s3AlertC.toDouble(),
                                    "catalyst_b2s3_temp_c" to
                                        (if (prefs.catB2s3SimC > 0f) prefs.catB2s3SimC
                                        else com.veplayer.app.vehicle.CatalystB2S3Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s3TempC
                                        )?.toDouble(),
                                    "catalyst_b2s3" to
                                        com.veplayer.app.vehicle.CatalystB2S3.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S3Monitor.state.value,
                                        ),
                                    "cat_b1s4_warn_c" to prefs.catB1s4WarnC.toDouble(),
                                    "cat_b1s4_alert_c" to prefs.catB1s4AlertC.toDouble(),
                                    "catalyst_b1s4_temp_c" to
                                        (if (prefs.catB1s4SimC > 0f) prefs.catB1s4SimC
                                        else com.veplayer.app.vehicle.CatalystB1S4Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s4TempC
                                        )?.toDouble(),
                                    "catalyst_b1s4" to
                                        com.veplayer.app.vehicle.CatalystB1S4.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S4Monitor.state.value,
                                        ),
                                    "cat_b2s4_warn_c" to prefs.catB2s4WarnC.toDouble(),
                                    "cat_b2s4_alert_c" to prefs.catB2s4AlertC.toDouble(),
                                    "catalyst_b2s4_temp_c" to
                                        (if (prefs.catB2s4SimC > 0f) prefs.catB2s4SimC
                                        else com.veplayer.app.vehicle.CatalystB2S4Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s4TempC
                                        )?.toDouble(),
                                    "catalyst_b2s4" to
                                        com.veplayer.app.vehicle.CatalystB2S4.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S4Monitor.state.value,
                                        ),
                                    "stft2_b1_warn_pct" to prefs.stft2B1WarnPct.toDouble(),
                                    "stft2_b1_alert_pct" to prefs.stft2B1AlertPct.toDouble(),
                                    "stft2_b1_speed_min_kmh" to prefs.stft2B1SpeedMinKmh.toDouble(),
                                    "fuel_trim_stft2_b1_pct" to
                                        (if (prefs.stft2B1SimPct != 0f) prefs.stft2B1SimPct
                                        else com.veplayer.app.vehicle.FuelTrimStft2B1Monitor.state.value.trimPct
                                            ?: snap.fuelTrimStft2B1Pct
                                        )?.toDouble(),
                                    "stft2_b1" to
                                        com.veplayer.app.vehicle.FuelTrimStft2B1.toJsonMap(
                                            com.veplayer.app.vehicle.FuelTrimStft2B1Monitor.state.value,
                                        ),
                                    "ltft2_b1_warn_pct" to prefs.ltft2B1WarnPct.toDouble(),
                                    "ltft2_b1_alert_pct" to prefs.ltft2B1AlertPct.toDouble(),
                                    "ltft2_b1_speed_min_kmh" to prefs.ltft2B1SpeedMinKmh.toDouble(),
                                    "fuel_trim_ltft2_b1_pct" to
                                        (if (prefs.ltft2B1SimPct != 0f) prefs.ltft2B1SimPct
                                        else com.veplayer.app.vehicle.FuelTrimLtft2B1Monitor.state.value.trimPct
                                            ?: snap.fuelTrimLtft2B1Pct
                                        )?.toDouble(),
                                    "ltft2_b1" to
                                        com.veplayer.app.vehicle.FuelTrimLtft2B1.toJsonMap(
                                            com.veplayer.app.vehicle.FuelTrimLtft2B1Monitor.state.value,
                                        ),
                                    "stft2_b2_warn_pct" to prefs.stft2B2WarnPct.toDouble(),
                                    "stft2_b2_alert_pct" to prefs.stft2B2AlertPct.toDouble(),
                                    "stft2_b2_speed_min_kmh" to prefs.stft2B2SpeedMinKmh.toDouble(),
                                    "fuel_trim_stft2_b2_pct" to
                                        (if (prefs.stft2B2SimPct != 0f) prefs.stft2B2SimPct
                                        else com.veplayer.app.vehicle.FuelTrimStft2B2Monitor.state.value.trimPct
                                            ?: snap.fuelTrimStft2B2Pct
                                        )?.toDouble(),
                                    "stft2_b2" to
                                        com.veplayer.app.vehicle.FuelTrimStft2B2.toJsonMap(
                                            com.veplayer.app.vehicle.FuelTrimStft2B2Monitor.state.value,
                                        ),
                                    "ltft2_b2_warn_pct" to prefs.ltft2B2WarnPct.toDouble(),
                                    "ltft2_b2_alert_pct" to prefs.ltft2B2AlertPct.toDouble(),
                                    "ltft2_b2_speed_min_kmh" to prefs.ltft2B2SpeedMinKmh.toDouble(),
                                    "fuel_trim_ltft2_b2_pct" to
                                        (if (prefs.ltft2B2SimPct != 0f) prefs.ltft2B2SimPct
                                        else com.veplayer.app.vehicle.FuelTrimLtft2B2Monitor.state.value.trimPct
                                            ?: snap.fuelTrimLtft2B2Pct
                                        )?.toDouble(),
                                    "ltft2_b2" to
                                        com.veplayer.app.vehicle.FuelTrimLtft2B2.toJsonMap(
                                            com.veplayer.app.vehicle.FuelTrimLtft2B2Monitor.state.value,
                                        ),
                                    "cat_b1s5_warn_c" to prefs.catB1s5WarnC.toDouble(),
                                    "cat_b1s5_alert_c" to prefs.catB1s5AlertC.toDouble(),
                                    "catalyst_b1s5_temp_c" to
                                        (if (prefs.catB1s5SimC > 0f) prefs.catB1s5SimC
                                        else com.veplayer.app.vehicle.CatalystB1S5Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s5TempC
                                        )?.toDouble(),
                                    "catalyst_b1s5" to
                                        com.veplayer.app.vehicle.CatalystB1S5.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S5Monitor.state.value,
                                        ),
                                    "cat_b2s5_warn_c" to prefs.catB2s5WarnC.toDouble(),
                                    "cat_b2s5_alert_c" to prefs.catB2s5AlertC.toDouble(),
                                    "catalyst_b2s5_temp_c" to
                                        (if (prefs.catB2s5SimC > 0f) prefs.catB2s5SimC
                                        else com.veplayer.app.vehicle.CatalystB2S5Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s5TempC
                                        )?.toDouble(),
                                    "catalyst_b2s5" to
                                        com.veplayer.app.vehicle.CatalystB2S5.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S5Monitor.state.value,
                                        ),
                                    "inject_warn_deg" to prefs.injectWarnDeg.toDouble(),
                                    "inject_alert_deg" to prefs.injectAlertDeg.toDouble(),
                                    "inject_speed_min_kmh" to prefs.injectSpeedMinKmh.toDouble(),
                                    "fuel_inject_timing_deg" to
                                        (if (prefs.injectSimDeg != 0f) prefs.injectSimDeg
                                        else com.veplayer.app.vehicle.FuelInjectTimingMonitor.state.value.timingDeg
                                            ?: snap.fuelInjectTimingDeg
                                        )?.toDouble(),
                                    "fuel_inject" to
                                        com.veplayer.app.vehicle.FuelInjectTiming.toJsonMap(
                                            com.veplayer.app.vehicle.FuelInjectTimingMonitor.state.value,
                                        ),
                                    "hybrid_warn_pct" to prefs.hybridWarnPct.toDouble(),
                                    "hybrid_alert_pct" to prefs.hybridAlertPct.toDouble(),
                                    "hybrid_speed_min_kmh" to prefs.hybridSpeedMinKmh.toDouble(),
                                    "hybrid_batt_life_pct" to
                                        (if (prefs.hybridSimPct > 0f) prefs.hybridSimPct
                                        else com.veplayer.app.vehicle.HybridBattLifeMonitor.state.value.lifePct
                                            ?: snap.hybridBattLifePct
                                        )?.toDouble(),
                                    "hybrid_batt" to
                                        com.veplayer.app.vehicle.HybridBattLife.toJsonMap(
                                            com.veplayer.app.vehicle.HybridBattLifeMonitor.state.value,
                                        ),
                                    "ref_torque_warn_low_nm" to prefs.refTorqueWarnLowNm.toDouble(),
                                    "ref_torque_alert_low_nm" to prefs.refTorqueAlertLowNm.toDouble(),
                                    "ref_torque_warn_high_nm" to prefs.refTorqueWarnHighNm.toDouble(),
                                    "ref_torque_alert_high_nm" to prefs.refTorqueAlertHighNm.toDouble(),
                                    "engine_ref_torque_nm" to
                                        (if (prefs.refTorqueSimNm > 0f) prefs.refTorqueSimNm
                                        else com.veplayer.app.vehicle.EngineRefTorqueMonitor.state.value.torqueNm
                                            ?: snap.engineRefTorqueNm
                                        )?.toDouble(),
                                    "ref_torque" to
                                        com.veplayer.app.vehicle.EngineRefTorque.toJsonMap(
                                            com.veplayer.app.vehicle.EngineRefTorqueMonitor.state.value,
                                        ),
                                    "cat_b1s6_warn_c" to prefs.catB1s6WarnC.toDouble(),
                                    "cat_b1s6_alert_c" to prefs.catB1s6AlertC.toDouble(),
                                    "catalyst_b1s6_temp_c" to
                                        (if (prefs.catB1s6SimC > 0f) prefs.catB1s6SimC
                                        else com.veplayer.app.vehicle.CatalystB1S6Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s6TempC
                                        )?.toDouble(),
                                    "catalyst_b1s6" to
                                        com.veplayer.app.vehicle.CatalystB1S6.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S6Monitor.state.value,
                                        ),
                                    "cat_b2s6_warn_c" to prefs.catB2s6WarnC.toDouble(),
                                    "cat_b2s6_alert_c" to prefs.catB2s6AlertC.toDouble(),
                                    "catalyst_b2s6_temp_c" to
                                        (if (prefs.catB2s6SimC > 0f) prefs.catB2s6SimC
                                        else com.veplayer.app.vehicle.CatalystB2S6Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s6TempC
                                        )?.toDouble(),
                                    "catalyst_b2s6" to
                                        com.veplayer.app.vehicle.CatalystB2S6.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S6Monitor.state.value,
                                        ),
                                    "thr_b_warn_pct" to prefs.thrBWarnPct.toDouble(),
                                    "thr_b_alert_pct" to prefs.thrBAlertPct.toDouble(),
                                    "thr_b_speed_min_kmh" to prefs.thrBSpeedMinKmh.toDouble(),
                                    "throttle_b_pct" to
                                        (if (prefs.thrBSimPct > 0f) prefs.thrBSimPct
                                        else com.veplayer.app.vehicle.ThrottleBMonitor.state.value.throttlePct
                                            ?: snap.throttleBPct
                                        )?.toDouble(),
                                    "throttle_b" to
                                        com.veplayer.app.vehicle.ThrottleB.toJsonMap(
                                            com.veplayer.app.vehicle.ThrottleBMonitor.state.value,
                                        ),
                                    "thr_c_warn_pct" to prefs.thrCWarnPct.toDouble(),
                                    "thr_c_alert_pct" to prefs.thrCAlertPct.toDouble(),
                                    "thr_c_speed_min_kmh" to prefs.thrCSpeedMinKmh.toDouble(),
                                    "throttle_c_pct" to
                                        (if (prefs.thrCSimPct > 0f) prefs.thrCSimPct
                                        else com.veplayer.app.vehicle.ThrottleCMonitor.state.value.throttlePct
                                            ?: snap.throttleCPct
                                        )?.toDouble(),
                                    "throttle_c" to
                                        com.veplayer.app.vehicle.ThrottleC.toJsonMap(
                                            com.veplayer.app.vehicle.ThrottleCMonitor.state.value,
                                        ),
                                    "mil_time_warn_min" to prefs.milTimeWarnMin,
                                    "mil_time_alert_min" to prefs.milTimeAlertMin,
                                    "mil_time_min" to
                                        (if (prefs.milTimeSimMin > 0) prefs.milTimeSimMin
                                        else com.veplayer.app.vehicle.MilTimeOnMonitor.state.value.minutes
                                            ?: snap.milTimeMin
                                        ),
                                    "mil_time" to
                                        com.veplayer.app.vehicle.MilTimeOn.toJsonMap(
                                            com.veplayer.app.vehicle.MilTimeOnMonitor.state.value,
                                        ),
                                    "cat_b1s7_warn_c" to prefs.catB1s7WarnC.toDouble(),
                                    "cat_b1s7_alert_c" to prefs.catB1s7AlertC.toDouble(),
                                    "catalyst_b1s7_temp_c" to
                                        (if (prefs.catB1s7SimC > 0f) prefs.catB1s7SimC
                                        else com.veplayer.app.vehicle.CatalystB1S7Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s7TempC
                                        )?.toDouble(),
                                    "catalyst_b1s7" to
                                        com.veplayer.app.vehicle.CatalystB1S7.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S7Monitor.state.value,
                                        ),
                                    "cat_b2s7_warn_c" to prefs.catB2s7WarnC.toDouble(),
                                    "cat_b2s7_alert_c" to prefs.catB2s7AlertC.toDouble(),
                                    "catalyst_b2s7_temp_c" to
                                        (if (prefs.catB2s7SimC > 0f) prefs.catB2s7SimC
                                        else com.veplayer.app.vehicle.CatalystB2S7Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s7TempC
                                        )?.toDouble(),
                                    "catalyst_b2s7" to
                                        com.veplayer.app.vehicle.CatalystB2S7.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S7Monitor.state.value,
                                        ),
                                    "fuel_type_expected" to prefs.fuelTypeExpected,
                                    "fuel_type_speed_min_kmh" to prefs.fuelTypeSpeedMinKmh.toDouble(),
                                    "fuel_type_code" to
                                        (if (prefs.fuelTypeSimCode > 0) prefs.fuelTypeSimCode
                                        else com.veplayer.app.vehicle.FuelTypeMonitor.state.value.typeCode
                                            ?: snap.fuelTypeCode
                                        ),
                                    "fuel_type" to
                                        com.veplayer.app.vehicle.FuelType.toJsonMap(
                                            com.veplayer.app.vehicle.FuelTypeMonitor.state.value,
                                        ),
                                    "max_equiv_warn_low" to prefs.maxEquivWarnLow.toDouble(),
                                    "max_equiv_alert_low" to prefs.maxEquivAlertLow.toDouble(),
                                    "max_equiv_warn_high" to prefs.maxEquivWarnHigh.toDouble(),
                                    "max_equiv_alert_high" to prefs.maxEquivAlertHigh.toDouble(),
                                    "max_equiv_ratio" to
                                        (if (prefs.maxEquivSimRatio != 0f) prefs.maxEquivSimRatio
                                        else com.veplayer.app.vehicle.MaxEquivRatioMonitor.state.value.ratio
                                            ?: snap.maxEquivRatio
                                        )?.toDouble(),
                                    "max_equiv" to
                                        com.veplayer.app.vehicle.MaxEquivRatio.toJsonMap(
                                            com.veplayer.app.vehicle.MaxEquivRatioMonitor.state.value,
                                        ),
                                    "max_maf_warn_low_gps" to prefs.maxMafWarnLowGps.toDouble(),
                                    "max_maf_alert_low_gps" to prefs.maxMafAlertLowGps.toDouble(),
                                    "max_maf_gps" to
                                        (if (prefs.maxMafSimGps > 0f) prefs.maxMafSimGps
                                        else com.veplayer.app.vehicle.MaxMafGpsMonitor.state.value.mafGps
                                            ?: snap.maxMafGps
                                        )?.toDouble(),
                                    "max_maf" to
                                        com.veplayer.app.vehicle.MaxMafGps.toJsonMap(
                                            com.veplayer.app.vehicle.MaxMafGpsMonitor.state.value,
                                        ),
                                    "cat_b1s8_warn_c" to prefs.catB1s8WarnC.toDouble(),
                                    "cat_b1s8_alert_c" to prefs.catB1s8AlertC.toDouble(),
                                    "catalyst_b1s8_temp_c" to
                                        (if (prefs.catB1s8SimC > 0f) prefs.catB1s8SimC
                                        else com.veplayer.app.vehicle.CatalystB1S8Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s8TempC
                                        )?.toDouble(),
                                    "catalyst_b1s8" to
                                        com.veplayer.app.vehicle.CatalystB1S8.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S8Monitor.state.value,
                                        ),
                                    "cat_b2s8_warn_c" to prefs.catB2s8WarnC.toDouble(),
                                    "cat_b2s8_alert_c" to prefs.catB2s8AlertC.toDouble(),
                                    "catalyst_b2s8_temp_c" to
                                        (if (prefs.catB2s8SimC > 0f) prefs.catB2s8SimC
                                        else com.veplayer.app.vehicle.CatalystB2S8Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s8TempC
                                        )?.toDouble(),
                                    "catalyst_b2s8" to
                                        com.veplayer.app.vehicle.CatalystB2S8.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S8Monitor.state.value,
                                        ),
                                    "max_avail_torque_warn_low" to prefs.maxAvailTorqueWarnLow.toDouble(),
                                    "max_avail_torque_alert_low" to prefs.maxAvailTorqueAlertLow.toDouble(),
                                    "max_avail_torque_speed_min_kmh" to prefs.maxAvailTorqueSpeedMinKmh.toDouble(),
                                    "max_avail_torque_pct" to
                                        (if (prefs.maxAvailTorqueSimPct != 0f) prefs.maxAvailTorqueSimPct
                                        else com.veplayer.app.vehicle.MaxAvailTorqueMonitor.state.value.torquePct
                                            ?: snap.maxAvailTorquePct
                                        )?.toDouble(),
                                    "max_avail_torque" to
                                        com.veplayer.app.vehicle.MaxAvailTorque.toJsonMap(
                                            com.veplayer.app.vehicle.MaxAvailTorqueMonitor.state.value,
                                        ),
                                    "maf_iat_warn_c" to prefs.mafIatWarnC.toDouble(),
                                    "maf_iat_alert_c" to prefs.mafIatAlertC.toDouble(),
                                    "maf_iat_speed_min_kmh" to prefs.mafIatSpeedMinKmh.toDouble(),
                                    "maf_sensor_iat_c" to
                                        (if (prefs.mafIatSimC > 0f) prefs.mafIatSimC
                                        else com.veplayer.app.vehicle.MafSensorIatMonitor.state.value.tempC
                                            ?: snap.mafSensorIatC
                                        )?.toDouble(),
                                    "maf_iat" to
                                        com.veplayer.app.vehicle.MafSensorIat.toJsonMap(
                                            com.veplayer.app.vehicle.MafSensorIatMonitor.state.value,
                                        ),
                                    "aux_input_alert_mask" to prefs.auxInputAlertMask,
                                    "aux_input_speed_min_kmh" to prefs.auxInputSpeedMinKmh.toDouble(),
                                    "aux_input_status" to
                                        (if (prefs.auxInputSimCode > 0) prefs.auxInputSimCode
                                        else com.veplayer.app.vehicle.AuxInputStatusMonitor.state.value.statusCode
                                            ?: snap.auxInputStatus
                                        ),
                                    "aux_input" to
                                        com.veplayer.app.vehicle.AuxInputStatus.toJsonMap(
                                            com.veplayer.app.vehicle.AuxInputStatusMonitor.state.value,
                                        ),
                                    "cat_b1s9_warn_c" to prefs.catB1s9WarnC.toDouble(),
                                    "cat_b1s9_alert_c" to prefs.catB1s9AlertC.toDouble(),
                                    "catalyst_b1s9_temp_c" to
                                        (if (prefs.catB1s9SimC > 0f) prefs.catB1s9SimC
                                        else com.veplayer.app.vehicle.CatalystB1S9Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s9TempC
                                        )?.toDouble(),
                                    "catalyst_b1s9" to
                                        com.veplayer.app.vehicle.CatalystB1S9.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S9Monitor.state.value,
                                        ),
                                    "cat_b2s9_warn_c" to prefs.catB2s9WarnC.toDouble(),
                                    "cat_b2s9_alert_c" to prefs.catB2s9AlertC.toDouble(),
                                    "catalyst_b2s9_temp_c" to
                                        (if (prefs.catB2s9SimC > 0f) prefs.catB2s9SimC
                                        else com.veplayer.app.vehicle.CatalystB2S9Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s9TempC
                                        )?.toDouble(),
                                    "catalyst_b2s9" to
                                        com.veplayer.app.vehicle.CatalystB2S9.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S9Monitor.state.value,
                                        ),
                                    "ect2_warn_c" to prefs.ect2WarnC.toDouble(),
                                    "ect2_alert_c" to prefs.ect2AlertC.toDouble(),
                                    "coolant_ect2_c" to
                                        (if (prefs.ect2SimC > 0f) prefs.ect2SimC
                                        else com.veplayer.app.vehicle.CoolantEct2Monitor.state.value.coolantC
                                            ?: snap.coolantEct2C
                                        )?.toDouble(),
                                    "ect2" to
                                        com.veplayer.app.vehicle.CoolantEct2.toJsonMap(
                                            com.veplayer.app.vehicle.CoolantEct2Monitor.state.value,
                                        ),
                                    "iat2_warn_c" to prefs.iat2WarnC.toDouble(),
                                    "iat2_alert_c" to prefs.iat2AlertC.toDouble(),
                                    "iat2_speed_min_kmh" to prefs.iat2SpeedMinKmh.toDouble(),
                                    "iat_sensor2_c" to
                                        (if (prefs.iat2SimC > 0f) prefs.iat2SimC
                                        else com.veplayer.app.vehicle.IatSensor2Monitor.state.value.tempC
                                            ?: snap.iatSensor2C
                                        )?.toDouble(),
                                    "iat2" to
                                        com.veplayer.app.vehicle.IatSensor2.toJsonMap(
                                            com.veplayer.app.vehicle.IatSensor2Monitor.state.value,
                                        ),
                                    "turbo_inlet_warn_kpa" to prefs.turboInletWarnKpa.toDouble(),
                                    "turbo_inlet_alert_kpa" to prefs.turboInletAlertKpa.toDouble(),
                                    "turbo_inlet_speed_min_kmh" to prefs.turboInletSpeedMinKmh.toDouble(),
                                    "turbo_inlet_kpa" to
                                        (if (prefs.turboInletSimKpa > 0f) prefs.turboInletSimKpa
                                        else com.veplayer.app.vehicle.TurboInletPressureMonitor.state.value.pressureKpa
                                            ?: snap.turboInletKpa
                                        )?.toDouble(),
                                    "turbo_inlet" to
                                        com.veplayer.app.vehicle.TurboInletPressure.toJsonMap(
                                            com.veplayer.app.vehicle.TurboInletPressureMonitor.state.value,
                                        ),
                                    "cat_b1s10_warn_c" to prefs.catB1s10WarnC.toDouble(),
                                    "cat_b1s10_alert_c" to prefs.catB1s10AlertC.toDouble(),
                                    "catalyst_b1s10_temp_c" to
                                        (if (prefs.catB1s10SimC > 0f) prefs.catB1s10SimC
                                        else com.veplayer.app.vehicle.CatalystB1S10Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s10TempC
                                        )?.toDouble(),
                                    "catalyst_b1s10" to
                                        com.veplayer.app.vehicle.CatalystB1S10.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S10Monitor.state.value,
                                        ),
                                    "cat_b2s10_warn_c" to prefs.catB2s10WarnC.toDouble(),
                                    "cat_b2s10_alert_c" to prefs.catB2s10AlertC.toDouble(),
                                    "catalyst_b2s10_temp_c" to
                                        (if (prefs.catB2s10SimC > 0f) prefs.catB2s10SimC
                                        else com.veplayer.app.vehicle.CatalystB2S10Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s10TempC
                                        )?.toDouble(),
                                    "catalyst_b2s10" to
                                        com.veplayer.app.vehicle.CatalystB2S10.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S10Monitor.state.value,
                                        ),
                                    "egr_temp_warn_c" to prefs.egrTempWarnC.toDouble(),
                                    "egr_temp_alert_c" to prefs.egrTempAlertC.toDouble(),
                                    "egr_temp_speed_min_kmh" to prefs.egrTempSpeedMinKmh.toDouble(),
                                    "egr_temp_c" to
                                        (if (prefs.egrTempSimC > 0f) prefs.egrTempSimC
                                        else com.veplayer.app.vehicle.EgrTemperatureMonitor.state.value.tempC
                                            ?: snap.egrTempC
                                        )?.toDouble(),
                                    "egr_temp" to
                                        com.veplayer.app.vehicle.EgrTemperature.toJsonMap(
                                            com.veplayer.app.vehicle.EgrTemperatureMonitor.state.value,
                                        ),
                                    "diesel_iaf_warn_pct" to prefs.dieselIafWarnPct.toDouble(),
                                    "diesel_iaf_alert_pct" to prefs.dieselIafAlertPct.toDouble(),
                                    "diesel_iaf_speed_min_kmh" to prefs.dieselIafSpeedMinKmh.toDouble(),
                                    "diesel_iaf_cmd_pct" to
                                        (if (prefs.dieselIafSimPct > 0f) prefs.dieselIafSimPct
                                        else com.veplayer.app.vehicle.DieselIntakeAirflowMonitor.state.value.flowPct
                                            ?: snap.dieselIafCmdPct
                                        )?.toDouble(),
                                    "diesel_iaf" to
                                        com.veplayer.app.vehicle.DieselIntakeAirflow.toJsonMap(
                                            com.veplayer.app.vehicle.DieselIntakeAirflowMonitor.state.value,
                                        ),
                                    "thr_act_warn_pct" to prefs.thrActWarnPct.toDouble(),
                                    "thr_act_alert_pct" to prefs.thrActAlertPct.toDouble(),
                                    "thr_act_speed_min_kmh" to prefs.thrActSpeedMinKmh.toDouble(),
                                    "thr_actuator_pct" to
                                        (if (prefs.thrActSimPct > 0f) prefs.thrActSimPct
                                        else com.veplayer.app.vehicle.ThrottleActuatorMonitor.state.value.actuatorPct
                                            ?: snap.thrActuatorPct
                                        )?.toDouble(),
                                    "thr_act" to
                                        com.veplayer.app.vehicle.ThrottleActuator.toJsonMap(
                                            com.veplayer.app.vehicle.ThrottleActuatorMonitor.state.value,
                                        ),
                                    "cat_b1s11_warn_c" to prefs.catB1s11WarnC.toDouble(),
                                    "cat_b1s11_alert_c" to prefs.catB1s11AlertC.toDouble(),
                                    "catalyst_b1s11_temp_c" to
                                        (if (prefs.catB1s11SimC > 0f) prefs.catB1s11SimC
                                        else com.veplayer.app.vehicle.CatalystB1S11Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s11TempC
                                        )?.toDouble(),
                                    "catalyst_b1s11" to
                                        com.veplayer.app.vehicle.CatalystB1S11.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S11Monitor.state.value,
                                        ),
                                    "cat_b2s11_warn_c" to prefs.catB2s11WarnC.toDouble(),
                                    "cat_b2s11_alert_c" to prefs.catB2s11AlertC.toDouble(),
                                    "catalyst_b2s11_temp_c" to
                                        (if (prefs.catB2s11SimC > 0f) prefs.catB2s11SimC
                                        else com.veplayer.app.vehicle.CatalystB2S11Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s11TempC
                                        )?.toDouble(),
                                    "catalyst_b2s11" to
                                        com.veplayer.app.vehicle.CatalystB2S11.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S11Monitor.state.value,
                                        ),
                                    "egr_actual_warn_pct" to prefs.egrActualWarnPct.toDouble(),
                                    "egr_actual_alert_pct" to prefs.egrActualAlertPct.toDouble(),
                                    "egr_actual_speed_min_kmh" to prefs.egrActualSpeedMinKmh.toDouble(),
                                    "actual_egr_pct" to
                                        (if (prefs.egrActualSimPct > 0f) prefs.egrActualSimPct
                                        else com.veplayer.app.vehicle.ActualEgrMonitor.state.value.egrPct
                                            ?: snap.actualEgrPct
                                        )?.toDouble(),
                                    "egr_actual" to
                                        com.veplayer.app.vehicle.ActualEgr.toJsonMap(
                                            com.veplayer.app.vehicle.ActualEgrMonitor.state.value,
                                        ),
                                    "inject_ctrl_warn_kpa" to prefs.injectCtrlWarnKpa.toDouble(),
                                    "inject_ctrl_alert_kpa" to prefs.injectCtrlAlertKpa.toDouble(),
                                    "inject_ctrl_speed_min_kmh" to prefs.injectCtrlSpeedMinKmh.toDouble(),
                                    "inject_ctrl_kpa" to
                                        (if (prefs.injectCtrlSimKpa > 0f) prefs.injectCtrlSimKpa
                                        else com.veplayer.app.vehicle.InjectPressureControlMonitor.state.value.pressureKpa
                                            ?: snap.injectCtrlKpa
                                        )?.toDouble(),
                                    "inject_ctrl" to
                                        com.veplayer.app.vehicle.InjectPressureControl.toJsonMap(
                                            com.veplayer.app.vehicle.InjectPressureControlMonitor.state.value,
                                        ),
                                    "fuel_ctrl_warn_kpa" to prefs.fuelCtrlWarnKpa.toDouble(),
                                    "fuel_ctrl_alert_kpa" to prefs.fuelCtrlAlertKpa.toDouble(),
                                    "fuel_ctrl_speed_min_kmh" to prefs.fuelCtrlSpeedMinKmh.toDouble(),
                                    "fuel_ctrl_kpa" to
                                        (if (prefs.fuelCtrlSimKpa > 0f) prefs.fuelCtrlSimKpa
                                        else com.veplayer.app.vehicle.FuelPressureControlMonitor.state.value.pressureKpa
                                            ?: snap.fuelCtrlKpa
                                        )?.toDouble(),
                                    "fuel_ctrl" to
                                        com.veplayer.app.vehicle.FuelPressureControl.toJsonMap(
                                            com.veplayer.app.vehicle.FuelPressureControlMonitor.state.value,
                                        ),
                                    "cat_b1s12_warn_c" to prefs.catB1s12WarnC.toDouble(),
                                    "cat_b1s12_alert_c" to prefs.catB1s12AlertC.toDouble(),
                                    "catalyst_b1s12_temp_c" to
                                        (if (prefs.catB1s12SimC > 0f) prefs.catB1s12SimC
                                        else com.veplayer.app.vehicle.CatalystB1S12Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s12TempC
                                        )?.toDouble(),
                                    "catalyst_b1s12" to
                                        com.veplayer.app.vehicle.CatalystB1S12.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S12Monitor.state.value,
                                        ),
                                    "cat_b2s12_warn_c" to prefs.catB2s12WarnC.toDouble(),
                                    "cat_b2s12_alert_c" to prefs.catB2s12AlertC.toDouble(),
                                    "catalyst_b2s12_temp_c" to
                                        (if (prefs.catB2s12SimC > 0f) prefs.catB2s12SimC
                                        else com.veplayer.app.vehicle.CatalystB2S12Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s12TempC
                                        )?.toDouble(),
                                    "catalyst_b2s12" to
                                        com.veplayer.app.vehicle.CatalystB2S12.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S12Monitor.state.value,
                                        ),
                                    "stft_b2_warn_pct" to prefs.stftB2WarnPct.toDouble(),
                                    "stft_b2_alert_pct" to prefs.stftB2AlertPct.toDouble(),
                                    "stft_b2_speed_min_kmh" to prefs.stftB2SpeedMinKmh.toDouble(),
                                    "fuel_trim_stft_b2_pct" to
                                        (if (prefs.stftB2SimPct != 0f) prefs.stftB2SimPct
                                        else com.veplayer.app.vehicle.FuelTrimStftB2Monitor.state.value.trimPct
                                            ?: snap.fuelTrimStftB2Pct
                                        )?.toDouble(),
                                    "stft_b2" to
                                        com.veplayer.app.vehicle.FuelTrimStftB2.toJsonMap(
                                            com.veplayer.app.vehicle.FuelTrimStftB2Monitor.state.value,
                                        ),
                                    "ltft_b2_warn_pct" to prefs.ltftB2WarnPct.toDouble(),
                                    "ltft_b2_alert_pct" to prefs.ltftB2AlertPct.toDouble(),
                                    "ltft_b2_speed_min_kmh" to prefs.ltftB2SpeedMinKmh.toDouble(),
                                    "fuel_trim_ltft_b2_pct" to
                                        (if (prefs.ltftB2SimPct != 0f) prefs.ltftB2SimPct
                                        else com.veplayer.app.vehicle.FuelTrimLtftB2Monitor.state.value.trimPct
                                            ?: snap.fuelTrimLtftB2Pct
                                        )?.toDouble(),
                                    "ltft_b2" to
                                        com.veplayer.app.vehicle.FuelTrimLtftB2.toJsonMap(
                                            com.veplayer.app.vehicle.FuelTrimLtftB2Monitor.state.value,
                                        ),
                                    "cat_b1s13_warn_c" to prefs.catB1s13WarnC.toDouble(),
                                    "cat_b1s13_alert_c" to prefs.catB1s13AlertC.toDouble(),
                                    "catalyst_b1s13_temp_c" to
                                        (if (prefs.catB1s13SimC > 0f) prefs.catB1s13SimC
                                        else com.veplayer.app.vehicle.CatalystB1S13Monitor.state.value.catalystTempC
                                            ?: snap.catalystB1s13TempC
                                        )?.toDouble(),
                                    "catalyst_b1s13" to
                                        com.veplayer.app.vehicle.CatalystB1S13.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB1S13Monitor.state.value,
                                        ),
                                    "cat_b2s13_warn_c" to prefs.catB2s13WarnC.toDouble(),
                                    "cat_b2s13_alert_c" to prefs.catB2s13AlertC.toDouble(),
                                    "catalyst_b2s13_temp_c" to
                                        (if (prefs.catB2s13SimC > 0f) prefs.catB2s13SimC
                                        else com.veplayer.app.vehicle.CatalystB2S13Monitor.state.value.catalystTempC
                                            ?: snap.catalystB2s13TempC
                                        )?.toDouble(),
                                    "catalyst_b2s13" to
                                        com.veplayer.app.vehicle.CatalystB2S13.toJsonMap(
                                            com.veplayer.app.vehicle.CatalystB2S13Monitor.state.value,
                                        ),
                                    "dpf_trigger_warn_pct" to prefs.dpfTrigWarnPct.toDouble(),
                                    "dpf_trigger_alert_pct" to prefs.dpfTrigAlertPct.toDouble(),
                                    "dpf_trigger_speed_min_kmh" to prefs.dpfTrigSpeedMinKmh.toDouble(),
                                    "dpf_trigger_pct" to
                                        (if (prefs.dpfTrigSimPct > 0f) prefs.dpfTrigSimPct
                                        else com.veplayer.app.vehicle.DpfAftertreatmentMonitor.state.value.triggerPct
                                            ?: snap.dpfTriggerPct
                                        )?.toDouble(),
                                    "dpf_aftertreatment" to
                                        com.veplayer.app.vehicle.DpfAftertreatment.toJsonMap(
                                            com.veplayer.app.vehicle.DpfAftertreatmentMonitor.state.value,
                                        ),
                                    "thr_g_warn_pct" to prefs.thrGWarnPct.toDouble(),
                                    "thr_g_alert_pct" to prefs.thrGAlertPct.toDouble(),
                                    "thr_g_speed_min_kmh" to prefs.thrGSpeedMinKmh.toDouble(),
                                    "throttle_g_pct" to
                                        (if (prefs.thrGSimPct > 0f) prefs.thrGSimPct
                                        else com.veplayer.app.vehicle.ThrottleGMonitor.state.value.throttlePct
                                            ?: snap.throttleGPct
                                        )?.toDouble(),
                                    "throttle_g" to
                                        com.veplayer.app.vehicle.ThrottleG.toJsonMap(
                                            com.veplayer.app.vehicle.ThrottleGMonitor.state.value,
                                        ),
                                    "eng_friction_warn_pct" to prefs.engFrictionWarnPct.toDouble(),
                                    "eng_friction_alert_pct" to prefs.engFrictionAlertPct.toDouble(),
                                    "eng_friction_speed_min_kmh" to prefs.engFrictionSpeedMinKmh.toDouble(),
                                    "engine_friction_pct" to
                                        (if (prefs.engFrictionSimPct != 0f) prefs.engFrictionSimPct
                                        else com.veplayer.app.vehicle.EngineFrictionTorqueMonitor.state.value.frictionPct
                                            ?: snap.engineFrictionPct
                                        )?.toDouble(),
                                    "eng_friction" to
                                        com.veplayer.app.vehicle.EngineFrictionTorque.toJsonMap(
                                            com.veplayer.app.vehicle.EngineFrictionTorqueMonitor.state.value,
                                        ),
                                    "mil_dist_warn_km" to prefs.milDistWarnKm.toDouble(),
                                    "mil_dist_alert_km" to prefs.milDistAlertKm.toDouble(),
                                    "mil_distance_km" to
                                        (if (prefs.milDistSimKm > 0f) prefs.milDistSimKm
                                        else
                                            com.veplayer.app.vehicle.MilDistanceMonitor.state.value.distanceKm
                                                ?: snap.milDistanceKm
                                        )?.toDouble(),
                                    "mil_dist" to
                                        com.veplayer.app.vehicle.MilDistance.toJsonMap(
                                            com.veplayer.app.vehicle.MilDistanceMonitor.state.value,
                                        ),
                                    "dist_clear_warn_km" to prefs.distClearWarnKm.toDouble(),
                                    "dist_clear_alert_km" to prefs.distClearAlertKm.toDouble(),
                                    "dist_since_clear_km" to
                                        (if (prefs.distClearSimKm > 0f) prefs.distClearSimKm
                                        else
                                            com.veplayer.app.vehicle.DistSinceClearMonitor.state.value.distanceKm
                                                ?: snap.distSinceClearKm
                                        )?.toDouble(),
                                    "dist_since_clear" to
                                        com.veplayer.app.vehicle.DistSinceClear.toJsonMap(
                                            com.veplayer.app.vehicle.DistSinceClearMonitor.state.value,
                                        ),
                                    "rpm_warn" to prefs.rpmWarn.toDouble(),
                                    "rpm_alert" to prefs.rpmAlert.toDouble(),
                                    "rpm" to
                                        (if (prefs.rpmSim > 0f) prefs.rpmSim
                                        else com.veplayer.app.vehicle.RpmOverRevMonitor.state.value.rpm
                                            ?: snap.rpm
                                        )?.toDouble(),
                                    "engine_load_pct" to
                                        (if (prefs.engineLoadSimPct > 0f) prefs.engineLoadSimPct
                                        else com.veplayer.app.vehicle.EngineLoadMonitor.state.value.loadPct
                                            ?: snap.engineLoadPct
                                        )?.toDouble(),
                                    "engine_load_warn_pct" to prefs.engineLoadWarnPct.toDouble(),
                                    "engine_load_alert_pct" to prefs.engineLoadAlertPct.toDouble(),
                                    "engine_load_speed_min_kmh" to prefs.engineLoadSpeedMinKmh.toDouble(),
                                    "engine_load" to
                                        com.veplayer.app.vehicle.EngineLoad.toJsonMap(
                                            com.veplayer.app.vehicle.EngineLoadMonitor.state.value,
                                        ),
                                    "fuel_trim_stft_pct" to
                                        (if (prefs.stftSimPct != 0f) prefs.stftSimPct
                                        else com.veplayer.app.vehicle.FuelTrimStftMonitor.state.value.trimPct
                                            ?: snap.fuelTrimStftPct
                                        )?.toDouble(),
                                    "stft_warn_pct" to prefs.stftWarnPct.toDouble(),
                                    "stft_alert_pct" to prefs.stftAlertPct.toDouble(),
                                    "stft_speed_min_kmh" to prefs.stftSpeedMinKmh.toDouble(),
                                    "fuel_trim_stft" to
                                        com.veplayer.app.vehicle.FuelTrimStft.toJsonMap(
                                            com.veplayer.app.vehicle.FuelTrimStftMonitor.state.value,
                                        ),
                                    "fuel_trim_ltft_pct" to
                                        (if (prefs.ltftSimPct != 0f) prefs.ltftSimPct
                                        else com.veplayer.app.vehicle.FuelTrimLtftMonitor.state.value.trimPct
                                            ?: snap.fuelTrimLtftPct
                                        )?.toDouble(),
                                    "ltft_warn_pct" to prefs.ltftWarnPct.toDouble(),
                                    "ltft_alert_pct" to prefs.ltftAlertPct.toDouble(),
                                    "ltft_speed_min_kmh" to prefs.ltftSpeedMinKmh.toDouble(),
                                    "fuel_trim_ltft" to
                                        com.veplayer.app.vehicle.FuelTrimLtft.toJsonMap(
                                            com.veplayer.app.vehicle.FuelTrimLtftMonitor.state.value,
                                        ),
                                    "map_kpa" to
                                        (if (prefs.mapSimKpa > 0f) prefs.mapSimKpa
                                        else com.veplayer.app.vehicle.MapPressureMonitor.state.value.mapKpa
                                            ?: snap.mapKpa
                                        )?.toDouble(),
                                    "map_warn_kpa" to prefs.mapWarnKpa.toDouble(),
                                    "map_alert_kpa" to prefs.mapAlertKpa.toDouble(),
                                    "map_speed_min_kmh" to prefs.mapSpeedMinKmh.toDouble(),
                                    "map_pressure" to
                                        com.veplayer.app.vehicle.MapPressure.toJsonMap(
                                            com.veplayer.app.vehicle.MapPressureMonitor.state.value,
                                        ),
                                    "throttle_pct" to
                                        (if (prefs.throttleSimPct > 0f) prefs.throttleSimPct
                                        else com.veplayer.app.vehicle.HighThrottleMonitor.state.value.throttlePct
                                            ?: snap.throttlePct
                                        )?.toDouble(),
                                    "throttle_warn_pct" to prefs.throttleWarnPct.toDouble(),
                                    "throttle_alert_pct" to prefs.throttleAlertPct.toDouble(),
                                    "throttle_alert_hold_sec" to prefs.throttleAlertHoldSec.toDouble(),
                                    "throttle_high_sec" to
                                        com.veplayer.app.vehicle.HighThrottleMonitor.state.value.highForSec.toDouble(),
                                    "throttle" to
                                        com.veplayer.app.vehicle.HighThrottle.toJsonMap(
                                            com.veplayer.app.vehicle.HighThrottleMonitor.state.value,
                                        ),
                                    "tow_moving_sec" to
                                        com.veplayer.app.vehicle.UnauthorizedMoveMonitor.state.value.movingForSec.toDouble(),
                                    "tow_speed_min_kmh" to prefs.towSpeedMinKmh.toDouble(),
                                    "tow_warn_sec" to prefs.towWarnSec.toDouble(),
                                    "tow_alert_sec" to prefs.towAlertSec.toDouble(),
                                    "pbrake_warn_kmh" to prefs.pbrakeWarnKmh.toDouble(),
                                    "pbrake_alert_kmh" to prefs.pbrakeAlertKmh.toDouble(),
                                    "parking_brake" to
                                        (prefs.pbrakeSim ||
                                            com.veplayer.app.vehicle.ParkingBrakeMovingMonitor.state.value.parkingBrake ||
                                            snap.parkingBrake),
                                    "gear_roll_warn_kmh" to prefs.gearRollWarnKmh.toDouble(),
                                    "gear_roll_alert_kmh" to prefs.gearRollAlertKmh.toDouble(),
                                    "gear" to
                                        (if (prefs.gearRollSim) prefs.gearRollSimGear
                                        else com.veplayer.app.vehicle.GearRollMonitor.state.value.gear.ifBlank {
                                            snap.gear.name
                                        }),
                                    "speed_kmh" to
                                        (when {
                                            prefs.pbrakeSim && prefs.pbrakeSimKmh > 0f -> prefs.pbrakeSimKmh
                                            prefs.gearRollSim && prefs.gearRollSimKmh > 0f -> prefs.gearRollSimKmh
                                            else -> snap.speedKmh
                                        }).toDouble(),
                                    "turn_stuck_sec" to
                                        com.veplayer.app.vehicle.TurnStuckMonitor.state.value.heldSec.toDouble(),
                                    "turn_stuck_side" to
                                        com.veplayer.app.vehicle.TurnStuckMonitor.state.value.side.ifBlank { null },
                                    "turn_stuck_warn_sec" to prefs.turnStuckWarnSec.toDouble(),
                                    "turn_stuck_alert_sec" to prefs.turnStuckAlertSec.toDouble(),
                                    "hazard_stuck_sec" to
                                        com.veplayer.app.vehicle.HazardStuckMonitor.state.value.heldSec.toDouble(),
                                    "hazard_stuck_warn_sec" to prefs.hazardStuckWarnSec.toDouble(),
                                    "hazard_stuck_alert_sec" to prefs.hazardStuckAlertSec.toDouble(),
                                    "fuel_drop_pct" to
                                        com.veplayer.app.vehicle.SuddenFuelDropMonitor.state.value.dropPct.toDouble(),
                                    "fuel_drop_warn_pct" to prefs.fuelDropWarnPct.toDouble(),
                                    "fuel_drop_alert_pct" to prefs.fuelDropAlertPct.toDouble(),
                                    "battery_warn_v" to prefs.battVoltWarnV.toDouble(),
                                    "battery_alert_v" to prefs.battVoltAlertV.toDouble(),
                                    "battery_voltage_v" to
                                        (if (prefs.battVoltSimV > 0f) prefs.battVoltSimV
                                        else com.veplayer.app.vehicle.BatteryVoltageMonitor.state.value.volts
                                            ?: snap.batteryVoltageV
                                        )?.toDouble(),
                                    "tpms_warn_psi" to prefs.tpmsWarnPsi.toDouble(),
                                    "tpms_alert_psi" to prefs.tpmsAlertPsi.toDouble(),
                                    "tpms" to
                                        run {
                                            val st = com.veplayer.app.vehicle.TpmsHudMonitor.state.value
                                            if (st.wheels.isNotEmpty()) {
                                                com.veplayer.app.vehicle.TpmsHud.toJsonMap(st)
                                            } else {
                                                snap.toJsonMap()["tpms"]
                                            }
                                        },
                                    "harsh" to com.veplayer.app.vehicle.HarshDriving.toJsonMap(
                                        com.veplayer.app.vehicle.HarshDrivingMonitor.state.value,
                                    ),
                                    "impact" to com.veplayer.app.vehicle.ImpactDetect.toJsonMap(
                                        com.veplayer.app.vehicle.ImpactDetectMonitor.state.value,
                                    ),
                                    "abs" to com.veplayer.app.vehicle.AbsHud.toJsonMap(
                                        com.veplayer.app.vehicle.AbsHudMonitor.state.value,
                                    ),
                                    "abs_active" to
                                        (prefs.absSim ||
                                            com.veplayer.app.vehicle.AbsHudMonitor.state.value.active ||
                                            snap.absActive),
                                    "abs_active_sec" to
                                        com.veplayer.app.vehicle.AbsHudMonitor.state.value.activeForSec.toDouble(),
                                    "abs_events" to
                                        com.veplayer.app.vehicle.AbsHudMonitor.state.value.events,
                                    "abs_warn_sec" to prefs.absWarnSec.toDouble(),
                                    "abs_alert_sec" to prefs.absAlertSec.toDouble(),
                                    "abs_alert_events" to prefs.absAlertEvents.toDouble(),
                                    "speed_limit_kmh" to
                                        com.veplayer.app.vehicle.SpeedHudMonitor.effectiveLimitKmh(prefs),
                                    "phone_link" to com.veplayer.app.phone.PhoneLinkBus.state.value.toJsonMap(),
                                    "brand" to com.veplayer.app.brand.BrandRepository.toJsonMap(prefs),
                                    "kiosk" to com.veplayer.app.kiosk.KioskController.healthSnapshot(this@SenseBridgeService),
                                    "field" to
                                        mapOf(
                                            "package" to packageName,
                                            "cams" to com.veplayer.app.camera.CameraCatalog.list(this@SenseBridgeService).size,
                                            "signal_source" to prefs.signalSource,
                                            "can" to com.veplayer.app.vehicle.can.CanLinkBus.state.value.state.name,
                                            "obd" to com.veplayer.app.vehicle.ObdLinkBus.state.value.state.name,
                                        ),
                                ),
                    ).getOrThrow()
                remote.handle(hb.commands)
                remote.handleAlerts(hb.alerts)
                com.veplayer.app.fleet.PanicBus.applyFromHeartbeat(
                    hb.panicOpen,
                    hb.panicAlertId,
                    hb.panicMessage,
                    hb.panicClipUrl,
                )
                if (hb.speedZoneId != null && hb.speedZoneMaxKmh != null) {
                    com.veplayer.app.vehicle.SpeedZoneBus.apply(
                        com.veplayer.app.vehicle.SpeedZoneBus.Zone(
                            id = hb.speedZoneId,
                            name = hb.speedZoneName ?: "Zona",
                            maxKmh = hb.speedZoneMaxKmh,
                        ),
                    )
                } else {
                    com.veplayer.app.vehicle.SpeedZoneBus.clear()
                }
                com.veplayer.app.fleet.ShiftTracker.tickLocal(prefs)
                com.veplayer.app.fleet.ShiftTracker.applyFromHeartbeat(hb.shiftJson)
                SilentOtaCoordinator.maybeApply(this@SenseBridgeService, hb.ota)
            }
        }
        try {
            val req =
                LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15_000L)
                    .setMinUpdateIntervalMillis(10_000L)
                    .setMinUpdateDistanceMeters(20f)
                    .build()
            fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startFg() {
        val id = "veplayer_sense"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(id, "VePlayer Sense", NotificationManager.IMPORTANCE_LOW))
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val n: Notification =
            NotificationCompat.Builder(this, id)
                .setContentTitle("VePlayer")
                .setContentText("Sensores + flota activos")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(open)
                .setOngoing(true)
                .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(42, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(42, n)
        }
    }

    private fun postPing(
        lat: Double,
        lng: Double,
        accuracy: Float,
        speed: Float?,
        activity: String,
    ) {
        val arr =
            JSONArray().put(
                JSONObject()
                    .put("lat", lat)
                    .put("lng", lng)
                    .put("accuracy_m", accuracy.toDouble())
                    .put("speed_mps", speed?.toDouble())
                    .put("activity", activity)
                    .put("device_bucket", prefs.dailyBucket())
                    .put("ts", System.currentTimeMillis() / 1000),
            )
        val body =
            JSONObject()
                .put("pings", arr)
                .toString()
                .toRequestBody("application/json".toMediaType())
        val req =
            Request.Builder()
                .url(prefs.senseflowUrl.trimEnd('/') + "/api/pings")
                .post(body)
                .build()
        runCatching { client.newCall(req).execute().close() }
    }
}
