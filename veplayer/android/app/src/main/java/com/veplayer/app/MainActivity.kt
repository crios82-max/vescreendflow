package com.veplayer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.veplayer.app.data.VePrefs
import com.veplayer.app.kiosk.KioskController
import com.veplayer.app.sense.SenseBridgeService
import com.veplayer.app.ui.VeDest
import com.veplayer.app.ui.screens.CamerasScreen
import com.veplayer.app.ui.screens.HomeScreen
import com.veplayer.app.ui.screens.MapScreen
import com.veplayer.app.ui.screens.PlayerScreen
import com.veplayer.app.ui.screens.RadioScreen
import com.veplayer.app.ui.screens.SettingsScreen
import com.veplayer.app.ui.screens.StoreScreen
import com.veplayer.app.ui.screens.YouTubeScreen
import com.veplayer.app.ui.theme.Mute
import com.veplayer.app.ui.theme.Night
import com.veplayer.app.ui.theme.Panel
import com.veplayer.app.ui.theme.Teal
import com.veplayer.app.ui.theme.VePlayerTheme
import com.veplayer.app.fleet.RemoteCommandBus
import com.veplayer.app.watchdog.WatchdogService
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersive()
        requestRuntimePermissions()
        KioskController.tryStartLockTask(this)

        val prefs = VePrefs(this)
        if (prefs.mockReverse || prefs.mockSpeedKmh > 0f) {
            VehicleState.applyMock(prefs.mockSpeedKmh, prefs.mockReverse)
        }
        ContextCompat.startForegroundService(this, Intent(this, SenseBridgeService::class.java))
        WatchdogService.start(this)

        setContent {
            VePlayerTheme {
                var dest by remember { mutableStateOf(VeDest.Home) }
                val vehicle by VehicleState.state.collectAsState()
                var fleetMsg by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    WatchdogService.touchUi(this@MainActivity)
                    RemoteCommandBus.messages.collectLatest { fleetMsg = it }
                }
                LaunchedEffect(dest) {
                    WatchdogService.touchUi(this@MainActivity)
                }

                // Marcha atrás → forzar cámaras (retrovisor/trasera)
                LaunchedEffect(vehicle.reverse) {
                    if (vehicle.reverse) dest = VeDest.Cameras
                }

                Column(modifier = Modifier.fillMaxSize().background(Night)) {
                    if (fleetMsg != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Teal)
                                .clickable { fleetMsg = null }
                                .padding(12.dp),
                        ) {
                            Text("Flota: $fleetMsg", color = Night, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Rail(
                        current = dest,
                        onSelect = { dest = it },
                        speedKmh = vehicle.speedKmh,
                        reverse = vehicle.reverse,
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(148.dp)
                            .background(Panel)
                            .padding(vertical = 16.dp, horizontal = 10.dp),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(16.dp),
                    ) {
                        when (dest) {
                            VeDest.Home -> HomeScreen(onOpen = { dest = it })
                            VeDest.Cameras -> CamerasScreen(preferRear = vehicle.reverse)
                            VeDest.Radio -> RadioScreen()
                            VeDest.YouTube -> YouTubeScreen()
                            VeDest.Store -> StoreScreen()
                            VeDest.Player -> PlayerScreen()
                            VeDest.Map -> MapScreen()
                            VeDest.Settings -> SettingsScreen()
                        }
                    }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enterImmersive()
        WatchdogService.touchUi(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    private fun requestRuntimePermissions() {
        val needed = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        val missing =
            needed.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { c ->
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )
    }
}

@androidx.compose.runtime.Composable
private fun Rail(
    current: VeDest,
    onSelect: (VeDest) -> Unit,
    speedKmh: Float,
    reverse: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "VePlayer",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Teal,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        Text(
            if (reverse) "⏪ REVERSE" else "${speedKmh.toInt()} km/h",
            style = MaterialTheme.typography.labelMedium,
            color = if (reverse) Teal else Mute,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        VeDest.entries.forEach { item ->
            val selected = item == current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Teal.copy(alpha = 0.22f) else Night.copy(alpha = 0.35f))
                    .clickable { onSelect(item) }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    item.label,
                    color = if (selected) Teal else Mute,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}
