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
import com.veplayer.app.vehicle.FuelRangeHud
import com.veplayer.app.vehicle.FuelRangeHudMonitor
import com.veplayer.app.vehicle.Gear
import com.veplayer.app.vehicle.GearRollMonitor
import com.veplayer.app.vehicle.IdleAlert
import com.veplayer.app.vehicle.IdleMonitor
import com.veplayer.app.vehicle.DtcMonitor
import com.veplayer.app.vehicle.CabinOvertempMonitor
import com.veplayer.app.vehicle.CoolantOverheatMonitor
import com.veplayer.app.vehicle.DoorAjarMonitor
import com.veplayer.app.vehicle.DriverScoreMonitor
import com.veplayer.app.vehicle.EcoLiveMonitor
import com.veplayer.app.vehicle.EngineLoadMonitor
import com.veplayer.app.vehicle.EngineRuntimeMonitor
import com.veplayer.app.vehicle.HarshDrivingMonitor
import com.veplayer.app.vehicle.HazardStuckMonitor
import com.veplayer.app.vehicle.HighThrottleMonitor
import com.veplayer.app.vehicle.ImpactDetectMonitor
import com.veplayer.app.vehicle.HvacClimateMonitor
import com.veplayer.app.vehicle.IceFrostMonitor
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
    val prefs = remember { VePrefs(LocalContext.current) }
    val context = LocalContext.current
    val fleet = remember { FleetClient(prefs) }
    val scope = rememberCoroutineScope()
    val hud by SpeedHudMonitor.state.collectAsState()
    val maint by MaintenanceMonitor.state.collectAsState()
    val fuelHud by FuelRangeHudMonitor.state.collectAsState()
    val idle by IdleMonitor.state.collectAsState()
    val dtc by DtcMonitor.state.collectAsState()
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
    val coolantHot by CoolantOverheatMonitor.state.collectAsState()
    val rpmHot by RpmOverRevMonitor.state.collectAsState()
    val engineLoad by EngineLoadMonitor.state.collectAsState()
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
    LaunchedEffect(Unit) {
        while (true) {
            val snap = com.veplayer.app.vehicle.VehicleState.state.value
            SpeedHudMonitor.tick(prefs, snap.speedKmh)
            MaintenanceMonitor.tick(prefs, snap.odometerKm)
            FuelRangeHudMonitor.tick(prefs, snap.fuelPct, snap.batterySocPct, snap.rangeKm)
            IdleMonitor.tick(prefs, snap.speedKmh, snap.ignition)
            DtcMonitor.tick(prefs, snap)
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
            CoolantOverheatMonitor.tick(prefs, snap)
            RpmOverRevMonitor.tick(prefs, snap)
            EngineLoadMonitor.tick(prefs, snap)
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
                if (coolantHot.showWarn) {
                    Text(
                        "Motor · ${coolantHot.label}",
                        color = Color(com.veplayer.app.vehicle.CoolantOverheat.accentArgb(coolantHot.band)),
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
