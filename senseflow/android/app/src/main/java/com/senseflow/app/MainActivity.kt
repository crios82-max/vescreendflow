package com.senseflow.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.senseflow.app.data.Prefs
import com.senseflow.app.service.SenseService
import com.senseflow.app.ui.SenseTheme

class MainActivity : ComponentActivity() {
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)

        setContent {
            SenseTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var sharing by remember { mutableStateOf(prefs.sharingEnabled) }
                    var apiBase by remember { mutableStateOf(prefs.apiBaseUrl) }
                    var status by remember { mutableStateOf("Listo") }

                    Column(Modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("SenseFlow", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Envía pings anónimos (tráfico + personas) y mira ambas capas en el mapa.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedTextField(
                                value = apiBase,
                                onValueChange = { apiBase = it },
                                label = { Text("API base URL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text("Compartir sensores")
                                Switch(
                                    checked = sharing,
                                    onCheckedChange = { enabled ->
                                        sharing = enabled
                                        prefs.sharingEnabled = enabled
                                        prefs.apiBaseUrl = apiBase.trim().trimEnd('/')
                                        if (enabled) {
                                            requestSensePermissions()
                                            startSenseService()
                                            status = "Compartiendo…"
                                        } else {
                                            stopSenseService()
                                            status = "Pausado"
                                        }
                                    },
                                )
                            }
                            Button(
                                onClick = {
                                    prefs.apiBaseUrl = apiBase.trim().trimEnd('/')
                                    status = "API → ${prefs.apiBaseUrl}"
                                },
                            ) {
                                Text("Guardar API")
                            }
                            Text(status, style = MaterialTheme.typography.labelLarge)
                        }

                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    webViewClient = WebViewClient()
                                    loadUrl(prefs.apiBaseUrl.trimEnd('/') + "/")
                                }
                            },
                            update = { web ->
                                val url = prefs.apiBaseUrl.trimEnd('/') + "/"
                                if (web.url != url) web.loadUrl(url)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                }
            }
        }
    }

    private fun requestSensePermissions() {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION,
        )
        if (Build.VERSION.SDK_INT >= 33) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (Build.VERSION.SDK_INT >= 29) {
            needed += Manifest.permission.ACCESS_BACKGROUND_LOCATION
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startSenseService() {
        val intent = Intent(this, SenseService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopSenseService() {
        stopService(Intent(this, SenseService::class.java))
    }
}
