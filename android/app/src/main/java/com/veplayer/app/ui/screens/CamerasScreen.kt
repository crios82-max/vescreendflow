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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.veplayer.app.camera.CamDevice
import com.veplayer.app.camera.CameraCatalog
import com.veplayer.app.ui.theme.Mist
import com.veplayer.app.ui.theme.Mute
import com.veplayer.app.ui.theme.Night
import com.veplayer.app.ui.theme.Panel
import com.veplayer.app.ui.theme.Teal

@Composable
fun CamerasScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val devices = remember { CameraCatalog.list(context) }
    val (a0, b0) = remember(devices) { CameraCatalog.pickDual(devices) }
    var camA by remember(devices) { mutableStateOf(a0) }
    var camB by remember(devices) { mutableStateOf(b0) }
    var dual by remember { mutableStateOf(b0 != null) }
    var status by remember { mutableStateOf("Listo") }
    var previewA by remember { mutableStateOf<PreviewView?>(null) }
    var previewB by remember { mutableStateOf<PreviewView?>(null) }

    val hasPermission =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

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

    DisposableEffect(camA?.id, camB?.id, dual, previewA, previewB, hasPermission) {
        if (!hasPermission || camA == null || previewA == null) {
            onDispose { }
        } else {
            val future = ProcessCameraProvider.getInstance(context)
            val exec = ContextCompat.getMainExecutor(context)
            val listener =
                Runnable {
                    try {
                        val provider = future.get()
                        provider.unbindAll()
                        val pA =
                            Preview.Builder().build().also {
                                it.surfaceProvider = previewA!!.surfaceProvider
                            }
                        val wantDual = dual && camB != null && previewB != null
                        if (wantDual) {
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
                                status = "Dual ConcurrentCamera · ${camA!!.label} + ${camB!!.label}"
                            } catch (_: Exception) {
                                // Fallback: primary only
                                provider.unbindAll()
                                provider.bindToLifecycle(lifecycleOwner, selectorFor(camA!!), pA)
                                status =
                                    "Este SoC no soporta dual concurrente — mostrando ${camA!!.label}. " +
                                        "USB/UVC externas aparecen si el kernel las registra como Camera2 EXTERNAL."
                            }
                        } else {
                            provider.bindToLifecycle(lifecycleOwner, selectorFor(camA!!), pA)
                            status = "Simple · ${camA!!.label}"
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
            "Retrovisor digital + trasera / USB. Dual vía ConcurrentCamera cuando el hardware lo permite.",
            color = Mute,
        )
        Text(
            if (devices.isEmpty()) "Sin cámaras Camera2 detectadas."
            else "Detectadas: ${devices.joinToString { it.label }}",
            color = Mute,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Dual", dual) { dual = true }
            Chip("Simple", !dual) { dual = false }
        }
        if (devices.isNotEmpty()) {
            Row(Modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                ) { previewA = it }
                if (dual) {
                    CamSurface(
                        label = camB?.label ?: "B",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) { previewB = it }
                }
            }
        }
    }
}

@Composable
private fun CamSurface(
    label: String,
    modifier: Modifier = Modifier,
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
        Text(
            label,
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
