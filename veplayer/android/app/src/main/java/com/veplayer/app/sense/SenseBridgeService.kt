package com.veplayer.app.sense

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
import com.veplayer.app.BuildConfig
import com.veplayer.app.MainActivity
import com.veplayer.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Lightweight SenseFlow bridge — anonymous pings while VePlayer runs. */
class SenseBridgeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private lateinit var bucket: String

    private val callback =
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val speed = if (loc.hasSpeed()) loc.speed else null
                val activity =
                    when {
                        speed == null -> "UNKNOWN"
                        speed >= 4f -> "IN_VEHICLE"
                        speed >= 0.5f -> "ON_FOOT"
                        else -> "STILL"
                    }
                scope.launch { postPing(loc.latitude, loc.longitude, loc.accuracy, speed, activity) }
            }
        }

    override fun onCreate() {
        super.onCreate()
        bucket = dailyBucket()
        startFg()
        try {
            val req =
                LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15_000L)
                    .setMinUpdateIntervalMillis(10_000L)
                    .setMinUpdateDistanceMeters(20f)
                    .build()
            fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startFg() {
        val id = "veplayer_sense"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(id, "VePlayer Sense", NotificationManager.IMPORTANCE_LOW))
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val n: Notification =
            NotificationCompat.Builder(this, id)
                .setContentTitle("VePlayer")
                .setContentText("Sensores de flota activos")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(open)
                .setOngoing(true)
                .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(42, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(42, n)
        }
    }

    private fun postPing(
        lat: Double,
        lng: Double,
        accuracy: Float,
        speed: Float?,
        activity: String,
    ) {
        val arr =
            JSONArray().put(
                JSONObject()
                    .put("lat", lat)
                    .put("lng", lng)
                    .put("accuracy_m", accuracy.toDouble())
                    .put("speed_mps", speed?.toDouble())
                    .put("activity", activity)
                    .put("device_bucket", bucket)
                    .put("ts", System.currentTimeMillis() / 1000),
            )
        val body =
            JSONObject()
                .put("pings", arr)
                .toString()
                .toRequestBody("application/json".toMediaType())
        val req =
            Request.Builder()
                .url(BuildConfig.SENSEFLOW_URL.trimEnd('/') + "/api/pings")
                .post(body)
                .build()
        runCatching { client.newCall(req).execute().close() }
    }

    private fun dailyBucket(): String {
        val sp = getSharedPreferences("veplayer", MODE_PRIVATE)
        var install = sp.getString("install_id", null)
        if (install.isNullOrBlank()) {
            install = UUID.randomUUID().toString()
            sp.edit().putString("install_id", install).apply()
        }
        val raw = "$install|${java.time.LocalDate.now()}"
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)
    }
}
