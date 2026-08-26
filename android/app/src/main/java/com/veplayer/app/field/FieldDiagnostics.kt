package com.veplayer.app.field

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.veplayer.app.BuildConfig
import com.veplayer.app.camera.CameraCatalog
import com.veplayer.app.data.VePrefs
import com.veplayer.app.kiosk.KioskController
import com.veplayer.app.vehicle.ObdLinkBus
import com.veplayer.app.vehicle.VehicleState
import com.veplayer.app.vehicle.can.CanLinkBus
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Snapshot for field commissioning: HW + kiosk + fleet reachability.
 */
data class FieldDiagReport(
    val lines: List<String>,
    val json: JSONObject,
) {
    fun asText(): String = lines.joinToString("\n")
}

object FieldDiagnostics {
    private val http =
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()

    fun collect(context: Context, pingNetwork: Boolean = true): FieldDiagReport {
        val prefs = VePrefs(context)
        val cams = CameraCatalog.list(context)
        val usb =
            runCatching {
                val um = context.getSystemService(UsbManager::class.java)
                um?.deviceList?.values?.map { d ->
                    "vid=${d.vendorId} pid=${d.productId} ${d.deviceName}"
                }.orEmpty()
            }.getOrDefault(emptyList())

        val btBonded =
            runCatching {
                if (Build.VERSION.SDK_INT >= 31 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    return@runCatching listOf("(sin permiso BT_CONNECT)")
                }
                val bm = context.getSystemService(BluetoothManager::class.java)
                bm?.adapter?.bondedDevices?.map { "${it.name ?: "?"} ${it.address}" }.orEmpty()
            }.getOrDefault(emptyList())

        val can = CanLinkBus.state.value
        val obd = ObdLinkBus.state.value
        val vehicle = VehicleState.state.value
        val kiosk = KioskController.healthSnapshot(context)

        var senseOk = false
        var senseDetail = "skip"
        if (pingNetwork) {
            val url = prefs.senseflowUrl.trimEnd('/') + "/api/health"
            runCatching {
                http.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                    senseOk = resp.isSuccessful
                    senseDetail = "HTTP ${resp.code}"
                }
            }.onFailure {
                senseOk = false
                senseDetail = it.message?.take(60) ?: "fail"
            }
        }

        val perms =
            listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) +
                if (Build.VERSION.SDK_INT >= 31) {
                    listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                } else {
                    emptyList()
                }

        val permLines =
            perms.map { p ->
                val ok =
                    ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
                "${if (ok) "✓" else "○"} ${p.substringAfterLast('.')}"
            }

        val lines =
            buildList {
                add("VePlayer ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                add("pkg ${context.packageName}")
                add("device ${prefs.deviceName} · id ${prefs.deviceId().take(12)}…")
                add(KioskController.statusLabel(context))
                add("source=${vehicle.source} · can=${can.state}/${can.backend} · ${can.text}")
                add("obd=${obd.state} · ${obd.text}")
                add("SenseFlow ${prefs.senseflowUrl} · $senseDetail")
                add("cams=${cams.size}: " + cams.joinToString { it.label })
                add("usb=${usb.size}: " + if (usb.isEmpty()) "—" else usb.joinToString("; "))
                add("bt bonded=${btBonded.size}: " + if (btBonded.isEmpty()) "—" else btBonded.take(4).joinToString("; "))
                addAll(permLines)
                add("OTA auto=${prefs.autoOtaEnabled} · last=${prefs.lastOtaStatus}")
                add("watchdog relaunches=${prefs.watchdogRelaunchCount}")
            }

        val json =
            JSONObject()
                .put("app_version", BuildConfig.VERSION_NAME)
                .put("version_code", BuildConfig.VERSION_CODE)
                .put("package", context.packageName)
                .put("device_id", prefs.deviceId())
                .put("device_name", prefs.deviceName)
                .put("senseflow_url", prefs.senseflowUrl)
                .put("sense_ok", senseOk)
                .put("sense_detail", senseDetail)
                .put("signal_source", prefs.signalSource)
                .put("can", JSONObject().put("state", can.state.name).put("backend", can.backend).put("text", can.text))
                .put("obd", JSONObject().put("state", obd.state.name).put("text", obd.text))
                .put(
                    "kiosk",
                    JSONObject().also { o ->
                        kiosk.forEach { (k, v) ->
                            when (v) {
                                null -> o.put(k, JSONObject.NULL)
                                is Boolean, is Number, is String -> o.put(k, v)
                                else -> o.put(k, v.toString())
                            }
                        }
                    },
                )
                .put(
                    "cameras",
                    JSONArray().also { arr ->
                        cams.forEach { c ->
                            arr.put(
                                JSONObject()
                                    .put("id", c.id)
                                    .put("label", c.label)
                                    .put("external", c.isExternal),
                            )
                        }
                    },
                )
                .put("usb", JSONArray(usb))
                .put("bt_bonded", JSONArray(btBonded))
                .put("ts", System.currentTimeMillis() / 1000)

        return FieldDiagReport(lines = lines, json = json)
    }
}
