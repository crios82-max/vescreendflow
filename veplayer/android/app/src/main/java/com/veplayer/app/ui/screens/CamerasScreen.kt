package com.veplayer.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.veplayer.app.ui.theme.Mist
import com.veplayer.app.ui.theme.Mute
import com.veplayer.app.ui.theme.Night
import com.veplayer.app.ui.theme.Panel
import com.veplayer.app.ui.theme.Teal

private data class CamOption(val label: String, val facing: Int)

@Composable
fun CamerasScreen() {
    val options =
        listOf(
            CamOption("Delantera / retrovisor", CameraSelector.LENS_FACING_FRONT),
            CamOption("Trasera", CameraSelector.LENS_FACING_BACK),
        )
    var selected by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Cámaras del vehículo", style = MaterialTheme.typography.headlineMedium, color = Mist, fontWeight = FontWeight.Bold)
        Text(
            "Frontal (retrovisor digital) y trasera. En head-unit se mapean a USB/UVC o CSI.",
            color = Mute,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, opt ->
                val on = selected == index
                Text(
                    opt.label,
                    color = if (on) Night else Mist,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) Teal else Panel)
                        .clickable { selected = index }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        CameraPreview(
            facing = options[selected].facing,
            title = options[selected].label,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp)),
        )
    }
}

@Composable
private fun CameraPreview(
    facing: Int,
    title: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var error by remember(facing) { mutableStateOf<String?>(null) }
    val hasPermission =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    Box(modifier = modifier.background(Panel)) {
        if (!hasPermission) {
            CenterMsg(title, "Permiso de cámara pendiente")
        } else {
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
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { previewView ->
                    val future = ProcessCameraProvider.getInstance(context)
                    future.addListener(
                        {
                            try {
                                val provider = future.get()
                                val preview =
                                    Preview.Builder()
                                        .setTargetResolution(Size(1280, 720))
                                        .build()
                                        .also { it.surfaceProvider = previewView.surfaceProvider }
                                val selector =
                                    CameraSelector.Builder().requireLensFacing(facing).build()
                                provider.unbindAll()
                                provider.bindToLifecycle(lifecycleOwner, selector, preview)
                                error = null
                            } catch (e: Exception) {
                                error = e.message ?: "Sin cámara en este dispositivo"
                            }
                        },
                        ContextCompat.getMainExecutor(context),
                    )
                },
            )
            if (error != null) CenterMsg(title, error!!)
        }
        Text(
            title,
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
private fun CenterMsg(title: String, message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Night),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Mist, fontWeight = FontWeight.Bold)
            Text(message, color = Mute)
        }
    }
}
