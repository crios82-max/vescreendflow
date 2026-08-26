package com.senseflow.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.senseflow.app.MainActivity
import com.senseflow.app.R
import com.senseflow.app.data.ApiClient
import com.senseflow.app.data.PingPayload
import com.senseflow.app.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

class SenseService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: Prefs
    private lateinit var api: ApiClient
    private val activityHint = AtomicReference("UNKNOWN")

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val locationCallback =
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val speed = if (loc.hasSpeed()) loc.speed else null
                val act = resolveActivity(activityHint.get(), speed)
                scope.launch {
                    api.postPings(
                        listOf(
                            PingPayload(
                                lat = loc.latitude,
                                lng = loc.longitude,
                                accuracyM = if (loc.hasAccuracy()) loc.accuracy else null,
                                speedMps = speed,
                                activity = act,
                                deviceBucket = prefs.deviceBucket(),
                            ),
                        ),
                    )
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        api = ApiClient { prefs.apiBaseUrl }
        startInForeground()
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ACTIVITY) {
            activityHint.set(intent.getStringExtra(EXTRA_ACTIVITY) ?: "UNKNOWN")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(locationCallback)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val channelId = "senseflow_share"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "SenseFlow", NotificationManager.IMPORTANCE_LOW),
        )
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification: Notification =
            NotificationCompat.Builder(this, channelId)
                .setContentTitle("SenseFlow compartiendo")
                .setContentText("Ubicación anónima → tráfico + personas")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(open)
                .setOngoing(true)
                .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                41,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(41, notification)
        }
    }

    private fun startLocationUpdates() {
        val request =
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 12_000L)
                .setMinUpdateIntervalMillis(8_000L)
                .setMinUpdateDistanceMeters(15f)
                .build()
        try {
            fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    companion object {
        const val ACTION_ACTIVITY = "com.senseflow.app.ACTION_ACTIVITY"
        const val EXTRA_ACTIVITY = "activity"

        fun resolveActivity(raw: String, speedMps: Float?): String {
            if (raw != "UNKNOWN") return raw
            val s = speedMps ?: return "UNKNOWN"
            return when {
                s >= 4f -> "IN_VEHICLE"
                s >= 0.5f -> "ON_FOOT"
                else -> "STILL"
            }
        }
    }
}
