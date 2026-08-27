package com.veplayer.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.veplayer.app.camera.BirdEyeCalibration
import com.veplayer.app.camera.CamDevice
import com.veplayer.app.camera.CamSlot
import com.veplayer.app.camera.CameraCatalog
import com.veplayer.app.camera.CameraSlots
import com.veplayer.app.data.VePrefs
import com.veplayer.app.ui.cameras.BirdEye360Panel
import com.veplayer.app.ui.cameras.ReverseGuidelinesOverlay
import com.veplayer.app.ui.theme.Mist
import com.veplayer.app.ui.theme.Mute
import com.veplayer.app.ui.theme.Night
import com.veplayer.app.ui.theme.Panel
import com.veplayer.app.ui.theme.Teal
import com.veplayer.app.vehicle.VehicleState

private enum class CamMode { SIMPLE, DUAL, SURROUND360 }

@Composable
fun CamerasScreen(preferRear: Boolean = false) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { VePrefs(context) }
    val devices = remember { CameraCatalog.list(context) }
    val slots = remember(devices) { CameraSlots.assign(devices) }
    val (a0, b0) = remember(devices) { CameraCatalog.pickDual(devices) }
    val initialA =
        remember(devices, preferRear) {
            if (preferRear) {
                devices.firstOrNull { it.facing == CameraCharacteristics.LENS_FACING_BACK } ?: a0
            } else {
                a0
            }
        }
    var camA by remember(devices, preferRear) { mutableStateOf(initialA) }
    var camB by remember(devices) { mutableStateOf(b0) }
    var mode by remember {
        mutableStateOf(
            when {
                preferRear -> CamMode.SIMPLE
                b0 != null -> CamMode.DUAL
                else -> CamMode.SIMPLE
            },
        )
    }
    var status by remember { mutableStateOf("Listo") }
    var previewA by remember { mutableStateOf<PreviewView?>(null) }
    var previewB by remember { mutableStateOf<PreviewView?>(null) }
    var previewFront by remember { mutableStateOf<PreviewView?>(null) }
    var previewRear by remember { mutableStateOf<PreviewView?>(null) }
    var maxAhead by remember { mutableStateOf(prefs.birdEyeMaxAheadM) }
    var maxLat by remember { mutableStateOf(prefs.birdEyeMaxLatM) }
    val vehicle by VehicleState.state.collectAsState()
    var guidesOn by remember { mutableStateOf(prefs.reverseGuidesEnabled) }
    var guideTrack by remember { mutableStateOf(prefs.reverseGuideTrack) }
    val parking by com.veplayer.app.vehicle.ParkingDistanceMonitor.state.collectAsState()
    LaunchedEffect(Unit) {
        while (true) {
            val rev = VehicleState.state.value.reverse
            com.veplayer.app.vehicle.ParkingDistanceMonitor.tick(prefs, rev)
            delay(200)
        }
    }

    val hasPermission =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    fun isRearFacing(device: CamDevice?): Boolean {
        if (device == null) return false
        return device.facing == CameraCharacteristics.LENS_FACING_BACK ||
            device.facing == CameraCharacteristics.LENS_FACING_EXTERNAL
    }

    val showGuides =
        guidesOn &&
            (preferRear || vehicle.reverse || (mode == CamMode.SIMPLE && isRearFacing(camA)))

    fun selectorFor(device: CamDevice): CameraSelector {
        val facing =
            when (device.facing) {
                CameraCharacteristics.LENS_FACING_FRONT -> CameraSelector.LENS_FACING_FRONT
                CameraCharacteristics.LENS_FACING_EXTERNAL ->
                    runCatching {
                        CameraSelector::class.java.getField("LENS_FACING_EXTERNAL").getInt(null)
                    }.getOrDefault(CameraSelector.LENS_FACING_BACK)
                else -> CameraSelector.LENS_FACING_BACK
            }
        return CameraSelector.Builder().requireLensFacing(facing).build()
    }

    val dual = mode == CamMode.DUAL
    val surround = mode == CamMode.SURROUND360

    DisposableEffect(
        camA?.id,
        camB?.id,
        mode,
        previewA,
        previewB,
        previewFront,
        previewRear,
        hasPermission,
        slots.front?.id,
        slots.rear?.id,
    ) {
        if (!hasPermission) {
            onDispose { }
        } else {
            val future = ProcessCameraProvider.getInstance(context)
            val exec = ContextCompat.getMainExecutor(context)
            val listener =
                Runnable {
                    try {
                        val provider = future.get()
                        provider.unbindAll()
                        when (mode) {
                            CamMode.SIMPLE -> {
                                if (camA == null || previewA == null) return@Runnable
                                val pA =
                                    Preview.Builder().build().also {
                                        it.surfaceProvider = previewA!!.surfaceProvider
                                    }
                                provider.bindToLifecycle(lifecycleOwner, selectorFor(camA!!), pA)
                                status = "Simple · ${camA!!.label}"
                            }
                            CamMode.DUAL -> {
                                if (camA == null || previewA == null) return@Runnable
                                val pA =
                                    Preview.Builder().build().also {
                                        it.surfaceProvider = previewA!!.surfaceProvider
                                    }
                                if (camB != null && previewB != null) {
                                    val pB =
                                        Preview.Builder().build().also {
                                            it.surfaceProvider = previewB!!.surfaceProvider
                                        }
                                    val configA =
                                        ConcurrentCamera.SingleCameraConfig(
                                            selectorFor(camA!!),
                                            UseCaseGroup.Builder().addUseCase(pA).build(),
                                            lifecycleOwner,
                                        )
                                    val configB =
                                        ConcurrentCamera.SingleCameraConfig(
                                            selectorFor(camB!!),
                                            UseCaseGroup.Builder().addUseCase(pB).build(),
                                            lifecycleOwner,
                                        )
                                    try {
                                        provider.bindToLifecycle(listOf(configA, configB))
                                        status = "Dual · ${camA!!.label} + ${camB!!.label}"
                                    } catch (_: Exception) {
                                        provider.unbindAll()
                                        provider.bindToLifecycle(lifecycleOwner, selectorFor(camA!!), pA)
                                        status = "SoC sin dual concurrente — ${camA!!.label}"
                                    }
                                } else {
                                    provider.bindToLifecycle(lifecycleOwner, selectorFor(camA!!), pA)
                                    status = "Dual incompleto · ${camA!!.label}"
                                }
                            }
                            CamMode.SURROUND360 -> {
                                val frontDev = slots.front ?: camA
                                val rearDev = slots.rear ?: camB
                                val pFront =
                                    previewFront?.let { pv ->
                                        frontDev?.let { dev ->
                                            Preview.Builder().build().also {
                                                it.surfaceProvider = pv.surfaceProvider
                                            } to dev
                                        }
                                    }
                                val pRear =
                                    previewRear?.let { pv ->
                                        rearDev?.let { dev ->
                                            Preview.Builder().build().also {
                                                it.surfaceProvider = pv.surfaceProvider
                                            } to dev
                                        }
                                    }
                                when {
                                    pFront != null && pRear != null -> {
                                        val configA =
                                            ConcurrentCamera.SingleCameraConfig(
                                                selectorFor(pFront.second),
                                                UseCaseGroup.Builder().addUseCase(pFront.first).build(),
                                                lifecycleOwner,
                                            )
                                        val configB =
                                            ConcurrentCamera.SingleCameraConfig(
                                                selectorFor(pRear.second),
                                                UseCaseGroup.Builder().addUseCase(pRear.first).build(),
                                                lifecycleOwner,
                                            )
                                        try {
                                            provider.bindToLifecycle(listOf(configA, configB))
                                            status =
                                                "360 · front+rear live · sides ${slots.left?.label ?: "—"} / ${slots.right?.label ?: "—"} (USB)"
                                        } catch (_: Exception) {
                                            provider.unbindAll()
                                            provider.bindToLifecycle(
                                                lifecycleOwner,
                                                selectorFor(pFront.second),
                                                pFront.first,
                                            )
                                            status = "360 · solo front (sin concurrente) · bird’s-eye overlay activo"
                                        }
                                    }
                                    pFront != null -> {
                                        provider.bindToLifecycle(
                                            lifecycleOwner,
                                            selectorFor(pFront.second),
                                            pFront.first,
                                        )
                                        status = "360 · solo ${pFront.second.label} + bird’s-eye"
                                    }
                                    else -> status = "360 · sin preview — bird’s-eye con SenseFlow/visión"
                                }
                            }
                        }
                    } catch (e: Exception) {
                        status = e.message ?: "Error de cámara"
                    }
                }
            future.addListener(listener, exec)
            onDispose {
                runCatching { future.get().unbindAll() }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Cámaras del vehículo", style = MaterialTheme.typography.headlineMedium, color = Mist, fontWeight = FontWeight.Bold)
        Text(
            "Simple · Dual ConcurrentCamera · 360 bird’s-eye · guías reverse.",
            color = Mute,
        )
        Text(
            if (devices.isEmpty()) "Sin cámaras Camera2 detectadas."
            else "Detectadas: ${devices.joinToString { it.label }}",
            color = Mute,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Simple", mode == CamMode.SIMPLE) { mode = CamMode.SIMPLE }
            Chip("Dual", mode == CamMode.DUAL) { mode = CamMode.DUAL }
            Chip("360", mode == CamMode.SURROUND360) { mode = CamMode.SURROUND360 }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (vehicle.reverse) "REVERSE · guías" else "Guías parking",
                color = if (vehicle.reverse) Teal else Mist,
                fontWeight = FontWeight.Bold,
            )
            Chip(if (guidesOn) "Guías ON" else "Guías OFF", guidesOn) {
                guidesOn = !guidesOn
                prefs.reverseGuidesEnabled = guidesOn
            }
        }
        if (guidesOn && mode != CamMode.SURROUND360) {
            Text("Ancho vías ${(guideTrack * 100).toInt()}%", color = Mute)
            Slider(
                value = guideTrack,
                onValueChange = {
                    guideTrack = it
                    prefs.reverseGuideTrack = it
                },
                valueRange = 0.30f..0.60f,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (mode != CamMode.SURROUND360 && devices.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("A", color = Teal, fontWeight = FontWeight.Bold)
                    devices.forEach { d -> Chip(d.label, camA?.id == d.id, true) { camA = d } }
                }
                if (dual) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("B", color = Teal, fontWeight = FontWeight.Bold)
                        devices.forEach { d -> Chip(d.label, camB?.id == d.id, true) { camB = d } }
                    }
                }
            }
        }

        if (surround) {
            Text(
                "Slots · F:${slots.front?.label ?: "—"} · R:${slots.rear?.label ?: "—"} · L:${slots.left?.label ?: "—"} · Ri:${slots.right?.label ?: "—"}",
                color = Mute,
            )
            Text("Calibración bird’s-eye (metros)", color = Teal, fontWeight = FontWeight.Bold)
            Text("Adelante ${maxAhead.toInt()} m", color = Mute)
            Slider(
                value = maxAhead,
                onValueChange = {
                    maxAhead = it
                    prefs.birdEyeMaxAheadM = it
                },
                valueRange = 15f..80f,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Lateral ±${maxLat.toInt()} m", color = Mute)
            Slider(
                value = maxLat,
                onValueChange = {
                    maxLat = it
                    prefs.birdEyeMaxLatM = it
                },
                valueRange = 6f..30f,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(status, color = Mute)

        if (!hasPermission) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Panel),
                contentAlignment = Alignment.Center,
            ) { Text("Permiso de cámara pendiente", color = Mute) }
        } else if (surround) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.28f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CamSurface(
                        label = CamSlot.FRONT.label,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) { previewFront = it }
                    CamSurface(
                        label = CamSlot.REAR.label,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        showGuides = guidesOn && (preferRear || vehicle.reverse),
                        showParkingHud = prefs.parkingHudEnabled && vehicle.reverse,
                        parkingState = parking,
                        steeringDeg = vehicle.steeringAngleDeg,
                        trackWidth = guideTrack,
                    ) { previewRear = it }
                }
                BirdEye360Panel(
                    calibration = BirdEyeCalibration(maxAheadM = maxAhead, maxLatM = maxLat),
                    modifier = Modifier
                        .weight(0.44f)
                        .fillMaxHeight(),
                )
                Column(
                    modifier = Modifier
                        .weight(0.28f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SideSlot(label = CamSlot.LEFT.label, device = slots.left, modifier = Modifier.weight(1f))
                    SideSlot(label = CamSlot.RIGHT.label, device = slots.right, modifier = Modifier.weight(1f))
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CamSurface(
                    label = camA?.label ?: "A",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    showGuides = showGuides || (guidesOn && vehicle.reverse && isRearFacing(camA)),
                    showParkingHud =
                        prefs.parkingHudEnabled &&
                            vehicle.reverse &&
                            (isRearFacing(camA) || preferRear || mode == CamMode.SIMPLE),
                    parkingState = parking,
                    steeringDeg = vehicle.steeringAngleDeg,
                    trackWidth = guideTrack,
                ) { previewA = it }
                if (dual) {
                    CamSurface(
                        label = camB?.label ?: "B",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        showGuides = guidesOn && (vehicle.reverse || preferRear) && isRearFacing(camB),
                        showParkingHud =
                            prefs.parkingHudEnabled && vehicle.reverse && isRearFacing(camB),
                        parkingState = parking,
                        steeringDeg = vehicle.steeringAngleDeg,
                        trackWidth = guideTrack,
                    ) { previewB = it }
                }
            }
        }
    }
}

@Composable
private fun SideSlot(
    label: String,
    device: CamDevice?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Teal, fontWeight = FontWeight.Bold)
            Text(
                device?.label ?: "Sin USB / side cam",
                color = Mute,
            )
            Text(
                if (device != null) "Conectá ConcurrentCamera 3+ o USB UVC" else "Placeholder FOV",
                color = Mute,
            )
        }
    }
}

@Composable
private fun CamSurface(
    label: String,
    modifier: Modifier = Modifier,
    showGuides: Boolean = false,
    showParkingHud: Boolean = false,
    parkingState: com.veplayer.app.vehicle.ParkingDistance.State =
        com.veplayer.app.vehicle.ParkingDistance.State(),
    steeringDeg: Float? = null,
    trackWidth: Float = 0.46f,
    onReady: (PreviewView) -> Unit,
) {
    Box(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Panel)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    onReady(this)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        ReverseGuidelinesOverlay(
            steeringDeg = steeringDeg,
            trackWidth = trackWidth,
            enabled = showGuides,
            modifier = Modifier.fillMaxSize(),
        )
        if (showParkingHud) {
            com.veplayer.app.ui.cameras.ParkingDistanceOverlay(
                state = parkingState,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            label +
                (if (showGuides) " · guías" else "") +
                (if (showParkingHud && parkingState.label.isNotBlank()) " · ${parkingState.label}" else ""),
            color = Teal,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Night.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, compact: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Night else Mist,
        fontWeight = FontWeight.Bold,
        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Teal else Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 6.dp else 10.dp),
    )
}
