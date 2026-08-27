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
                                    "rpm_warn" to prefs.rpmWarn.toDouble(),
                                    "rpm_alert" to prefs.rpmAlert.toDouble(),
                                    "rpm" to
                                        (if (prefs.rpmSim > 0f) prefs.rpmSim
                                        else com.veplayer.app.vehicle.RpmOverRevMonitor.state.value.rpm
                                            ?: snap.rpm
                                        )?.toDouble(),
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
                                    "rpm_warn" to prefs.rpmWarn.toDouble(),
                                    "rpm_alert" to prefs.rpmAlert.toDouble(),
                                    "rpm" to
                                        (if (prefs.rpmSim > 0f) prefs.rpmSim
                                        else com.veplayer.app.vehicle.RpmOverRevMonitor.state.value.rpm
                                            ?: snap.rpm
                                        )?.toDouble(),
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
