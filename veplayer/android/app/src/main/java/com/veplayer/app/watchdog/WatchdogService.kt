package com.veplayer.app.watchdog

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.veplayer.app.MainActivity
import com.veplayer.app.R
import com.veplayer.app.sense.SenseBridgeService

/**
 * Periodic health check: keep SenseBridge alive and bring UI back if needed.
 */
class WatchdogService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick =
        object : Runnable {
            override fun run() {
                ensureSense()
                maybeRelaunchUi()
                handler.postDelayed(this, INTERVAL_MS)
            }
        }

    override fun onCreate() {
        super.onCreate()
        startFg()
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureSense() {
        if (!isServiceRunning(SenseBridgeService::class.java.name)) {
            runCatching {
                ContextCompat.startForegroundService(this, Intent(this, SenseBridgeService::class.java))
            }
        }
    }

    private fun maybeRelaunchUi() {
        val am = getSystemService(ActivityManager::class.java) ?: return
        val now = SystemClock.elapsedRealtime()
        val last = getSharedPreferences("veplayer", MODE_PRIVATE).getLong(KEY_LAST_UI, 0L)
        // If UI heartbeat stale > 90s, relaunch MainActivity
        if (last > 0 && now - last > UI_STALE_MS) {
            val launch =
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
            startActivity(launch)
            getSharedPreferences("veplayer", MODE_PRIVATE).edit().putLong(KEY_LAST_UI, now).apply()
        }
        // Keep process importance if tasks empty
        if (am.appTasks.isNullOrEmpty()) {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun isServiceRunning(className: String): Boolean {
        val am = getSystemService(ActivityManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        return am.getRunningServices(50).any { it.service.className == className }
    }

    private fun startFg() {
        val id = "veplayer_watchdog"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(id, "VePlayer Watchdog", NotificationManager.IMPORTANCE_MIN))
        val n: Notification =
            NotificationCompat.Builder(this, id)
                .setContentTitle("VePlayer watchdog")
                .setContentText("Auto-recover activo")
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(43, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(43, n)
        }
    }

    companion object {
        private const val INTERVAL_MS = 30_000L
        private const val UI_STALE_MS = 90_000L
        const val KEY_LAST_UI = "watchdog_last_ui"

        fun touchUi(context: Context) {
            context.getSharedPreferences("veplayer", Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_UI, SystemClock.elapsedRealtime())
                .apply()
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WatchdogService::class.java))
        }
    }
}
