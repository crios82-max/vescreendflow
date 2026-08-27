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
                        FleetInbox.onDispatchMessage(prefs, text)
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
                        val silent = cmd.payload?.optBoolean("silent", true) != false
                        val code =
                            if (cmd.payload?.has("version_code") == true) {
                                cmd.payload.optInt("version_code")
                            } else {
                                null
                            }
                        if (url.isNotBlank()) {
                            onStatus("Cmd OTA silent=$silent $url")
                            OtaInstaller(context).downloadAndInstall(
                                apkUrl = url,
                                targetVersionCode = code,
                                silent = silent,
                            ) { onStatus(it) }
                        } else {
                            onStatus("Cmd OTA sin apk_url")
                        }
                    }
                    "lock_task" -> {
                        onStatus("Cmd lock_task")
                        main.post {
                            context.startActivity(
                                Intent(context, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .putExtra(com.veplayer.app.watchdog.WatchdogService.EXTRA_FORCE_LOCK, true),
                            )
                        }
                    }
                    "apply_kiosk" -> {
                        onStatus("Cmd apply_kiosk")
                        com.veplayer.app.kiosk.KioskController.applyOwnerPolicies(context)
                    }
                    "run_diag" -> {
                        onStatus("Cmd run_diag")
                        val report = com.veplayer.app.field.FieldDiagnostics.collect(context)
                        prefs.lastFieldDiag = report.asText()
                        RemoteCommandBus.publish("Diag campo listo")
                        notify("Diagnóstico campo", report.lines.take(3).joinToString(" · "))
                        onStatus(report.lines.take(4).joinToString(" | "))
                        Log.i(TAG, report.asText())
                    }
                    "fm_tune" -> {
                        val mhz = cmd.payload?.optDouble("mhz")
                        val khzPayload =
                            if (cmd.payload?.has("khz") == true) cmd.payload.optInt("khz") else null
                        val khz =
                            khzPayload
                                ?: mhz?.let { (it * 1000).toInt() }
                        if (khz != null) {
                            onStatus("Cmd fm_tune $khz")
                            main.post {
                                com.veplayer.app.media.VeMediaHub.playFm(freqKhz = khz)
                            }
                        } else {
                            val preset = cmd.payload?.optString("preset").orEmpty()
                            val st = com.veplayer.app.radio.fm.FmPresets.byId(preset)
                            if (st != null) {
                                onStatus("Cmd fm_tune preset $preset")
                                main.post {
                                    com.veplayer.app.media.VeMediaHub.playFm(station = st)
                                }
                            } else {
                                onStatus("fm_tune sin mhz/khz/preset")
                            }
                        }
                    }
                    "set_dbc" -> {
                        val url = cmd.payload?.optString("url").orEmpty()
                        val text = cmd.payload?.optString("text").orEmpty()
                        onStatus("Cmd set_dbc")
                        runCatching {
                            val body =
                                when {
                                    text.isNotBlank() -> text
                                    url.isNotBlank() -> {
                                        okhttp3.OkHttpClient()
                                            .newCall(okhttp3.Request.Builder().url(url).build())
                                            .execute()
                                            .use { resp ->
                                                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                                                resp.body?.string() ?: error("empty DBC")
                                            }
                                    }
                                    else -> error("set_dbc sin url/text")
                                }
                            val key =
                                com.veplayer.app.vehicle.can.dbc.DbcRepository.installCustom(
                                    context,
                                    body,
                                    "fleet.dbc",
                                )
                            prefs.dbcSource = key
                            com.veplayer.app.vehicle.can.CanSignalDecoder.reload(context)
                            CanBusManager.rebind()
                            onStatus("DBC instalado · $key")
                            RemoteCommandBus.publish("DBC flota aplicado")
                        }.onFailure {
                            onStatus("set_dbc fail: ${it.message}")
                            throw it
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
                    "set_driver" -> {
                        val clear = cmd.payload?.optBoolean("clear", false) == true
                        if (clear) {
                            runCatching { ShiftTracker.end(prefs) }
                            DriverSession.clear(prefs)
                            ShiftTracker.clearLocal()
                            onStatus("Cmd set_driver → clear")
                            RemoteCommandBus.publish("Conductor: sin asignar")
                        } else {
                            val parsed = cmd.payload?.let { DriverSession.parse(it) }
                            val code = cmd.payload?.optString("code").orEmpty()
                            when {
                                parsed != null -> {
                                    DriverSession.apply(prefs, parsed)
                                    runCatching { ShiftTracker.start(prefs, parsed.id) }
                                    onStatus("Cmd set_driver → ${parsed.code}")
                                    RemoteCommandBus.publish("Conductor → ${parsed.name}")
                                }
                                code.isNotBlank() -> {
                                    val r = DriverSession.login(prefs, code, null)
                                    r.onSuccess {
                                        onStatus("Cmd set_driver → ${it.code}")
                                        RemoteCommandBus.publish("Conductor → ${it.name}")
                                    }.onFailure {
                                        onStatus("set_driver fail: ${it.message}")
                                    }
                                }
                                else -> onStatus("set_driver sin code/id")
                            }
                        }
                    }
                    "set_speed_limit" -> {
                        val lim =
                            when {
                                cmd.payload?.has("kmh") == true -> cmd.payload.optInt("kmh", 50)
                                cmd.payload?.has("limit") == true -> cmd.payload.optInt("limit", 50)
                                else -> -1
                            }
                        if (lim in 10..160) {
                            prefs.speedLimitKmh = lim
                            prefs.speedHudEnabled = true
                            onStatus("Cmd set_speed_limit → $lim")
                            RemoteCommandBus.publish("Límite $lim km/h")
                            com.veplayer.app.nav.NavTts.speakNow("Límite de velocidad $lim kilómetros por hora.")
                        } else {
                            onStatus("set_speed_limit inválido")
                        }
                    }
                    "set_fuel_warn" -> {
                        val pct =
                            when {
                                cmd.payload?.has("pct") == true -> cmd.payload.optDouble("pct").toFloat()
                                cmd.payload?.has("warn_pct") == true ->
                                    cmd.payload.optDouble("warn_pct").toFloat()
                                else -> -1f
                            }
                        val crit =
                            when {
                                cmd.payload?.has("critical_pct") == true ->
                                    cmd.payload.optDouble("critical_pct").toFloat()
                                cmd.payload?.has("crit_pct") == true ->
                                    cmd.payload.optDouble("crit_pct").toFloat()
                                else -> pct / 2f
                            }
                        val rangeWarn =
                            when {
                                cmd.payload?.has("range_km") == true ->
                                    cmd.payload.optDouble("range_km").toFloat()
                                cmd.payload?.has("warn_range_km") == true ->
                                    cmd.payload.optDouble("warn_range_km").toFloat()
                                else -> prefs.rangeWarnKm
                            }
                        if (pct in 5f..50f) {
                            prefs.fuelWarnPct = pct
                            prefs.fuelCriticalPct = crit.coerceIn(2f, pct)
                            prefs.rangeWarnKm = rangeWarn.coerceIn(5f, 200f)
                            prefs.rangeCriticalKm = (rangeWarn / 2f).coerceIn(2f, prefs.rangeWarnKm)
                            prefs.fuelHudEnabled = true
                            onStatus("Cmd set_fuel_warn → $pct% / ${rangeWarn.toInt()} km")
                            RemoteCommandBus.publish("Aviso energía ${pct.toInt()}%")
                            com.veplayer.app.nav.NavTts.speakNow(
                                "Umbral de energía ${pct.toInt()} por ciento.",
                            )
                        } else {
                            onStatus("set_fuel_warn inválido")
                        }
                    }
                    "set_idle_warn" -> {
                        val warnSec =
                            when {
                                cmd.payload?.has("warn_sec") == true -> cmd.payload.optInt("warn_sec")
                                cmd.payload?.has("sec") == true -> cmd.payload.optInt("sec")
                                else -> -1
                            }
                        val alertSec =
                            when {
                                cmd.payload?.has("alert_sec") == true -> cmd.payload.optInt("alert_sec")
                                else -> if (warnSec > 0) warnSec * 2 else -1
                            }
                        if (warnSec in 30..3600) {
                            prefs.idleWarnSec = warnSec
                            prefs.idleAlertSec = alertSec.coerceIn(warnSec, 7200)
                            prefs.idleAlertEnabled = true
                            onStatus("Cmd set_idle_warn → ${warnSec}s / ${prefs.idleAlertSec}s")
                            RemoteCommandBus.publish("Idle warn ${warnSec}s")
                            com.veplayer.app.nav.NavTts.speakNow(
                                "Aviso de ralentí a $warnSec segundos.",
                            )
                        } else {
                            onStatus("set_idle_warn inválido")
                        }
                    }
                    "panic_ack" -> {
                        PanicBus.clear(speak = true)
                        onStatus("Cmd panic_ack")
                        RemoteCommandBus.publish("SOS confirmado por flota")
                    }
                    "service_done" -> {
                        val kind = cmd.payload?.optString("kind").orEmpty().trim().lowercase()
                        val odo =
                            when {
                                cmd.payload?.has("odo_km") == true ->
                                    cmd.payload.optDouble("odo_km").toFloat()
                                else ->
                                    com.veplayer.app.vehicle.VehicleState.state.value.odometerKm
                            }
                        if (kind.isBlank() || odo == null) {
                            onStatus("service_done inválido")
                        } else {
                            val items =
                                com.veplayer.app.vehicle.Maintenance.parseJson(prefs.maintenanceJson)
                            prefs.maintenanceJson =
                                com.veplayer.app.vehicle.Maintenance.toJson(
                                    com.veplayer.app.vehicle.Maintenance.recordService(items, kind, odo),
                                )
                            prefs.maintenanceEnabled = true
                            onStatus("Cmd service_done → $kind @ ${odo.toInt()} km")
                            RemoteCommandBus.publish("Servicio $kind registrado")
                            com.veplayer.app.nav.NavTts.speakNow(
                                "Servicio de $kind registrado a ${odo.toInt()} kilómetros.",
                            )
                        }
                    }
                    "set_maintenance" -> {
                        val kind = cmd.payload?.optString("kind").orEmpty().trim().lowercase()
                        if (kind.isBlank()) {
                            onStatus("set_maintenance sin kind")
                        } else {
                            val items =
                                com.veplayer.app.vehicle.Maintenance.parseJson(prefs.maintenanceJson)
                                    .toMutableList()
                            val idx = items.indexOfFirst { it.kind == kind }
                            val cur =
                                if (idx >= 0) {
                                    items[idx]
                                } else {
                                    com.veplayer.app.vehicle.Maintenance.Item(
                                        kind = kind,
                                        label = cmd.payload?.optString("label")?.ifBlank { kind } ?: kind,
                                        intervalKm = 10000f,
                                        lastServiceOdoKm = 0f,
                                    )
                                }
                            val next =
                                cur.copy(
                                    label =
                                        cmd.payload?.optString("label")?.takeIf { it.isNotBlank() }
                                            ?: cur.label,
                                    intervalKm =
                                        if (cmd.payload?.has("interval_km") == true) {
                                            cmd.payload.optDouble("interval_km").toFloat()
                                        } else {
                                            cur.intervalKm
                                        },
                                    lastServiceOdoKm =
                                        when {
                                            cmd.payload?.has("last_service_odo_km") == true ->
                                                cmd.payload.optDouble("last_service_odo_km").toFloat()
                                            cmd.payload?.has("last_odo_km") == true ->
                                                cmd.payload.optDouble("last_odo_km").toFloat()
                                            else -> cur.lastServiceOdoKm
                                        },
                                    warnKm =
                                        if (cmd.payload?.has("warn_km") == true) {
                                            cmd.payload.optDouble("warn_km").toFloat()
                                        } else {
                                            cur.warnKm
                                        },
                                    enabled =
                                        if (cmd.payload?.has("enabled") == true) {
                                            cmd.payload.optBoolean("enabled")
                                        } else {
                                            cur.enabled
                                        },
                                )
                            if (idx >= 0) items[idx] = next else items.add(next)
                            prefs.maintenanceJson =
                                com.veplayer.app.vehicle.Maintenance.toJson(items)
                            prefs.maintenanceEnabled = true
                            onStatus("Cmd set_maintenance → $kind")
                            RemoteCommandBus.publish("Mantenimiento $kind actualizado")
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
        val fresh = FleetInbox.onAlerts(prefs, alerts)
        for (a in fresh.take(3)) {
            val prefix = if (a.severity == "warn") "⚠ " else "ℹ "
            RemoteCommandBus.publish(prefix + a.text)
            if (a.severity == "warn") notify("Alerta flota", a.text)
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
