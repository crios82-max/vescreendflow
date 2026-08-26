package com.veplayer.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.RemoteCommandBus
import com.veplayer.app.kiosk.KioskController
import com.veplayer.app.sense.SenseBridgeService
import com.veplayer.app.ui.VeDest
import com.veplayer.app.ui.screens.CamerasScreen
import com.veplayer.app.ui.screens.PlayerScreen
import com.veplayer.app.ui.screens.RadioScreen
import com.veplayer.app.ui.screens.SettingsScreen
import com.veplayer.app.ui.screens.StoreScreen
import com.veplayer.app.ui.screens.YouTubeScreen
import com.veplayer.app.surround.SurroundVision
import com.veplayer.app.ui.shell.BottomDock
import com.veplayer.app.ui.shell.DriveVizPanel
import com.veplayer.app.ui.theme.Mist
import com.veplayer.app.ui.theme.Mute
import com.veplayer.app.ui.theme.Night
import com.veplayer.app.ui.theme.Teal
import com.veplayer.app.ui.theme.VePlayerTheme
import com.veplayer.app.vehicle.CanBusManager
import com.veplayer.app.vehicle.VehicleState
import com.veplayer.app.watchdog.WatchdogService
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    private var surroundVision: SurroundVision? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersive()
        requestRuntimePermissions()
        KioskController.tryStartLockTask(this)
        if (intent?.getBooleanExtra(WatchdogService.EXTRA_FORCE_LOCK, false) == true) {
            KioskController.tryStartLockTask(this)
        }

        val prefs = VePrefs(this)
        CanBusManager.start(this)
        if (prefs.signalSource == "gps" && (prefs.mockReverse || prefs.mockSpeedKmh > 0f)) {
            VehicleState.applyMock(prefs.mockSpeedKmh, prefs.mockReverse)
        }
        ContextCompat.startForegroundService(this, Intent(this, SenseBridgeService::class.java))
        WatchdogService.start(this)
        surroundVision = SurroundVision(this).also { it.start(this) }

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
                    if (dest == VeDest.Cameras) {
                        surroundVision?.stop()
                    } else {
                        surroundVision?.start(this@MainActivity)
                    }
                }
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
                                .padding(10.dp),
                        ) {
                            Text("Flota: $fleetMsg", color = Night, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Main stage
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        when (dest) {
                            VeDest.Home, VeDest.Map -> {
                                // Tesla cockpit: drive viz | map
                                Row(modifier = Modifier.fillMaxSize()) {
                                    DriveVizPanel(
                                        vehicle = vehicle,
                                        modifier = Modifier
                                            .weight(0.38f)
                                            .fillMaxHeight(),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(0.62f)
                                            .fillMaxHeight(),
                                    ) {
                                        MapPane()
                                        NavChrome()
                                    }
                                }
                            }
                            else -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                ) {
                                    when (dest) {
                                        VeDest.Cameras -> CamerasScreen(preferRear = vehicle.reverse)
                                        VeDest.Radio -> RadioScreen()
                                        VeDest.YouTube -> YouTubeScreen()
                                        VeDest.Store -> StoreScreen()
                                        VeDest.Player -> PlayerScreen()
                                        VeDest.Settings -> SettingsScreen()
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }

                    BottomDock(current = dest, onSelect = { dest = it })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(WatchdogService.EXTRA_FORCE_LOCK, false)) {
            KioskController.tryStartLockTask(this)
        }
    }

    override fun onDestroy() {
        surroundVision?.stop()
        surroundVision = null
        super.onDestroy()
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
        if (Build.VERSION.SDK_INT >= 31) {
            needed += Manifest.permission.BLUETOOTH_CONNECT
            needed += Manifest.permission.BLUETOOTH_SCAN
        }
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

@SuppressLint("SetJavaScriptEnabled")
@androidx.compose.runtime.Composable
private fun MapPane() {
    val context = LocalContext.current
    val prefs = remember { VePrefs(context) }
    val url =
        remember(prefs.senseflowUrl, prefs.navToLat, prefs.navDestName) {
            buildString {
                append(prefs.senseflowUrl.trimEnd('/'))
                append("/?auto=1")
                append("&from_lat=${prefs.navFromLat}&from_lng=${prefs.navFromLng}")
                append("&to_lat=${prefs.navToLat}&to_lng=${prefs.navToLng}")
                append("&dest_name=${java.net.URLEncoder.encode(prefs.navDestName, "UTF-8")}")
            }
        }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(0xFF000000.toInt())
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                loadUrl(url)
            }
        },
        update = { it.loadUrl(url) },
        modifier = Modifier.fillMaxSize(),
    )
}

@androidx.compose.runtime.Composable
private fun NavChrome() {
    val nav by com.veplayer.app.nav.NavEngine.route.collectAsState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xCC111111))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(nav.nextDistanceShort, color = Mist, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(nav.nextInstruction, color = Mute, fontSize = 14.sp)
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xCC111111))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                "${nav.etaLabel} · ${nav.durationLabel} · ${nav.distanceLabel}",
                color = Mist,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "Destino · ${nav.destinationName.ifBlank { "—" }} · ${nav.source}",
                color = Mute,
                fontSize = 12.sp,
            )
        }
    }
}
