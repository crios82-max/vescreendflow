package com.veplayer.app.fleet

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.veplayer.app.MainActivity
import com.veplayer.app.R
import com.veplayer.app.data.VePrefs
import com.veplayer.app.nav.NavEngine
import com.veplayer.app.ota.OtaInstaller
import com.veplayer.app.vehicle.CanBusManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

object RemoteCommandBus {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun publish(msg: String) {
        _messages.tryEmit(msg)
    }
}

class RemoteCommandExecutor(
    private val context: Context,
    private val fleet: FleetClient,
) {
    private val main = Handler(Looper.getMainLooper())
    private val prefs = VePrefs(context)

    fun handle(commands: List<FleetCommand>, onStatus: (String) -> Unit = {}) {
        if (commands.isEmpty()) return
        val done = mutableListOf<Long>()
        for (cmd in commands) {
            try {
                when (cmd.command) {
                    "restart" -> {
                        onStatus("Cmd restart")
                        main.post {
                            val i =
                                Intent(context, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }
                            context.startActivity(i)
                        }
                    }
                    "lock" -> {
                        onStatus("Cmd lock")
                        main.post {
                            context.startActivity(
                                Intent(context, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                            Toast.makeText(context, "Lock remoto", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "message" -> {
                        val text = cmd.payload?.optString("text") ?: "Mensaje flota"
                        onStatus("Cmd message: $text")
                        RemoteCommandBus.publish(text)
                        notify("Flota", text)
                    }
                    "wipe" -> {
                        onStatus("Cmd wipe")
                        main.post {
                            Toast.makeText(context, "Wipe remoto…", Toast.LENGTH_LONG).show()
                            val am = context.getSystemService(ActivityManager::class.java)
                            val ok = am?.clearApplicationUserData() == true
                            if (!ok) {
                                context.getSharedPreferences("veplayer", Context.MODE_PRIVATE).edit().clear().apply()
                                Toast.makeText(context, "Prefs borradas (sin Device Owner wipe)", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    "ota" -> {
                        val url = cmd.payload?.optString("apk_url").orEmpty()
                        if (url.isNotBlank()) {
                            onStatus("Cmd OTA $url")
                            OtaInstaller(context).downloadAndInstall(url) { onStatus(it) }
                        } else {
                            onStatus("Cmd OTA sin apk_url")
                        }
                    }
                    "set_source" -> {
                        val src = cmd.payload?.optString("source")?.lowercase().orEmpty()
                        if (src in setOf("gps", "mock", "can", "obd")) {
                            prefs.signalSource = src
                            CanBusManager.rebind()
                            onStatus("Cmd set_source → $src")
                            RemoteCommandBus.publish("Fuente señales → $src")
                        } else {
                            onStatus("set_source inválido: $src")
                        }
                    }
                    "reboot_obd" -> {
                        prefs.signalSource = "obd"
                        CanBusManager.rebind()
                        onStatus("Cmd reboot_obd")
                        RemoteCommandBus.publish("OBD reiniciado / rebind")
                        notify("Flota", "OBD rebind")
                    }
                    "nav_dest" -> {
                        val name = cmd.payload?.optString("name") ?: "Destino flota"
                        val lat = cmd.payload?.optDouble("lat")
                        val lng = cmd.payload?.optDouble("lng")
                        if (lat != null && lng != null && !lat.isNaN() && !lng.isNaN()) {
                            prefs.navEnabled = true
                            prefs.navDestName = name
                            prefs.navToLat = lat
                            prefs.navToLng = lng
                            CoroutineScope(Dispatchers.IO).launch { NavEngine.refresh() }
                            onStatus("Cmd nav_dest → $name")
                            RemoteCommandBus.publish("Nav → $name")
                        } else {
                            onStatus("nav_dest sin lat/lng")
                        }
                    }
                    else -> onStatus("Cmd desconocido ${cmd.command}")
                }
                done += cmd.id
            } catch (e: Exception) {
                Log.w(TAG, "command ${cmd.id} failed", e)
                fleet.ackCommands(listOf(cmd.id), "failed")
            }
        }
        if (done.isNotEmpty()) fleet.ackCommands(done, "acked")
    }

    fun handleAlerts(alerts: List<FleetAlert>) {
        for (a in alerts.take(3)) {
            val prefix = if (a.severity == "warn") "⚠ " else "ℹ "
            RemoteCommandBus.publish(prefix + a.message)
            if (a.severity == "warn") notify("Alerta flota", a.message)
        }
    }

    private fun notify(
        title: String,
        text: String,
    ) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val id = "veplayer_remote"
        nm.createNotificationChannel(NotificationChannel(id, "Flota", NotificationManager.IMPORTANCE_HIGH))
        nm.notify(
            99,
            NotificationCompat.Builder(context, id)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        private const val TAG = "RemoteCmd"
    }
}
