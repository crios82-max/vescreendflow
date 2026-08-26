package com.veplayer.app.watchdog

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.veplayer.app.MainActivity
import com.veplayer.app.R
import com.veplayer.app.data.VePrefs
import com.veplayer.app.kiosk.KioskController
import com.veplayer.app.sense.SenseBridgeService

/**
 * Hardened health loop: SenseBridge + UI + kiosk policies + self-reschedule.
 */
class WatchdogService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: VePrefs
    private var consecutiveRelaunches = 0

    private val tick =
        object : Runnable {
            override fun run() {
                runCatching { tickOnce() }
                handler.postDelayed(this, INTERVAL_MS)
            }
        }

    override fun onCreate() {
        super.onCreate()
        prefs = VePrefs(this)
        startFg()
        scheduleKeepAliveAlarm()
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_KEEPALIVE) {
            runCatching { tickOnce() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        scheduleKeepAliveAlarm(delayMs = 5_000L)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun tickOnce() {
        KioskController.applyOwnerPolicies(this)
        ensureSense()
        maybeRelaunchUi()
        prefs.watchdogLastTickAt = System.currentTimeMillis()
    }

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
        val stale = last > 0 && now - last > UI_STALE_MS
        val emptyTasks = am.appTasks.isNullOrEmpty()
        val needLock =
            KioskController.isDeviceOwner(this) && !KioskController.isLockTaskActive(this)

        if (!stale && !emptyTasks && !needLock) {
            consecutiveRelaunches = 0
            return
        }

        // Back off if we keep bouncing (avoid tight relaunch loops).
        if (consecutiveRelaunches >= 5) {
            val lastKick = prefs.watchdogLastKickAt
            if (System.currentTimeMillis() - lastKick < 120_000L) {
                Log.w(TAG, "relaunch backoff active")
                return
            }
            consecutiveRelaunches = 0
        }

        Log.i(TAG, "relaunch UI stale=$stale empty=$emptyTasks needLock=$needLock")
        val launch =
            Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
                putExtra(EXTRA_FORCE_LOCK, needLock)
            }
        startActivity(launch)
        getSharedPreferences("veplayer", MODE_PRIVATE).edit().putLong(KEY_LAST_UI, now).apply()
        consecutiveRelaunches++
        prefs.watchdogRelaunchCount = prefs.watchdogRelaunchCount + 1
        prefs.watchdogLastKickAt = System.currentTimeMillis()
    }

    private fun isServiceRunning(className: String): Boolean {
        val am = getSystemService(ActivityManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        return am.getRunningServices(80).any { it.service.className == className }
    }

    private fun scheduleKeepAliveAlarm(delayMs: Long = INTERVAL_MS * 2) {
        val am = getSystemService(AlarmManager::class.java) ?: return
        val pi =
            PendingIntent.getService(
                this,
                44,
                Intent(this, WatchdogService::class.java).setAction(ACTION_KEEPALIVE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val at = SystemClock.elapsedRealtime() + delayMs
        if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        } else {
            @Suppress("DEPRECATION")
            am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        }
    }

    private fun startFg() {
        val id = "veplayer_watchdog"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(id, "VePlayer Watchdog", NotificationManager.IMPORTANCE_MIN))
        val n: Notification =
            NotificationCompat.Builder(this, id)
                .setContentTitle("VePlayer watchdog")
                .setContentText("Kiosk + Sense auto-recover")
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
        private const val TAG = "Watchdog"
        private const val INTERVAL_MS = 20_000L
        private const val UI_STALE_MS = 60_000L
        const val KEY_LAST_UI = "watchdog_last_ui"
        const val ACTION_KEEPALIVE = "com.veplayer.app.WATCHDOG_KEEPALIVE"
        const val EXTRA_FORCE_LOCK = "force_lock_task"

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
