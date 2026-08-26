package com.veplayer.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.veplayer.app.data.VePrefs
import com.veplayer.app.ui.theme.Mist
import com.veplayer.app.ui.theme.Mute
import com.veplayer.app.ui.theme.Night
import com.veplayer.app.ui.theme.Panel
import com.veplayer.app.ui.theme.Teal
import com.veplayer.app.vehicle.VehicleState

@Composable
private fun MotionVideoGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { VePrefs(context) }
    val vehicle by VehicleState.state.collectAsState()
    val blocked = VehicleState.shouldBlockVideo(prefs.videoSpeedBlockKmh)

    if (blocked) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(Panel),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Text("Video bloqueado en movimiento", color = Teal, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (vehicle.reverse) "Marcha atrás activa — usa Cámaras"
                    else "Velocidad ${vehicle.speedKmh.toInt()} km/h ≥ ${prefs.videoSpeedBlockKmh.toInt()} km/h",
                    color = Mist,
                )
                Text("Umbral configurable en Ajustes (PIN).", color = Mute)
            }
        }
    } else {
        content()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerScreen() {
    val context = LocalContext.current
    val prefs = remember { VePrefs(context) }
    MotionVideoGate {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    loadUrl(prefs.playerUrl)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val prefs = remember { VePrefs(context) }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                loadUrl(prefs.senseflowUrl.trimEnd('/') + "/")
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeScreen() {
    MotionVideoGate {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "YouTube",
                style = MaterialTheme.typography.headlineMedium,
                color = Mist,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                "Reproductor oficial. Se bloquea en movimiento / reverse.",
                color = Mute,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(0xFF0B1220.toInt())
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl("https://m.youtube.com")
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            )
        }
    }
}
