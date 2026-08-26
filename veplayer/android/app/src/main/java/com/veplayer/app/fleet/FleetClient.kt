package com.veplayer.app.fleet

import com.veplayer.app.BuildConfig
import com.veplayer.app.data.VePrefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class OtaInfo(
    val updateAvailable: Boolean,
    val latestVersionName: String?,
    val latestVersionCode: Int?,
    val apkUrl: String?,
    val notes: String?,
)

data class FleetCommand(
    val id: Long,
    val command: String,
    val payload: JSONObject?,
)

data class HeartbeatResult(
    val ota: OtaInfo?,
    val commands: List<FleetCommand>,
    val alerts: List<FleetAlert> = emptyList(),
)

data class FleetAlert(
    val id: Long,
    val kind: String,
    val severity: String,
    val message: String,
)

class FleetClient(private val prefs: VePrefs) {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()

    private fun base(): String = prefs.senseflowUrl.trimEnd('/')

    fun register(): Result<String> =
        runCatching {
            val body =
                JSONObject()
                    .put("device_id", prefs.deviceId())
                    .put("name", prefs.deviceName)
                    .put("app_version", BuildConfig.VERSION_NAME)
                    .put("version_code", BuildConfig.VERSION_CODE)
                    .toString()
                    .toRequestBody(JSON)
            val req =
                Request.Builder()
                    .url(base() + "/api/fleet/register")
                    .post(body)
                    .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("register HTTP ${resp.code}: $text")
                val json = JSONObject(text)
                val code = json.getString("pair_code")
                prefs.setPairCode(code)
                code
            }
        }

    fun heartbeat(
        lat: Double? = null,
        lng: Double? = null,
        speedMps: Float? = null,
        reverse: Boolean? = null,
        vehicleSignals: Map<String, Any?>? = null,
    ): Result<HeartbeatResult> =
        runCatching {
            val payload =
                JSONObject()
                    .put("device_id", prefs.deviceId())
                    .put("app_version", BuildConfig.VERSION_NAME)
                    .put("version_code", BuildConfig.VERSION_CODE)
            if (lat != null) payload.put("lat", lat)
            if (lng != null) payload.put("lng", lng)
            if (speedMps != null) payload.put("speed_mps", speedMps.toDouble())
            if (reverse != null) payload.put("reverse", reverse)
            if (vehicleSignals != null) {
                payload.put("vehicle_signals", mapToJson(vehicleSignals))
            }
            if (prefs.driverId > 0) {
                payload.put("driver_id", prefs.driverId)
                if (prefs.driverCode.isNotBlank()) payload.put("driver_code", prefs.driverCode)
            }
            val req =
                Request.Builder()
                    .url(base() + "/api/fleet/heartbeat")
                    .post(payload.toString().toRequestBody(JSON))
                    .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("heartbeat HTTP ${resp.code}: $text")
                val json = JSONObject(text)
                val otaJson = json.optJSONObject("ota")
                val ota =
                    otaJson?.let {
                        OtaInfo(
                            updateAvailable = it.optBoolean("update_available"),
                            latestVersionName = it.optString("latest_version_name", null),
                            latestVersionCode =
                                if (it.has("latest_version_code")) it.getInt("latest_version_code") else null,
                            apkUrl = it.optString("apk_url", null),
                            notes = it.optString("notes", null),
                        )
                    }
                val cmds = mutableListOf<FleetCommand>()
                val arr = json.optJSONArray("commands") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val c = arr.getJSONObject(i)
                    val p = c.opt("payload")
                    cmds +=
                        FleetCommand(
                            id = c.getLong("id"),
                            command = c.getString("command"),
                            payload = p as? JSONObject ?: (p as? String)?.let { runCatching { JSONObject(it) }.getOrNull() },
                        )
                }
                val alerts = mutableListOf<FleetAlert>()
                val alertArr = json.optJSONArray("alerts") ?: JSONArray()
                for (i in 0 until alertArr.length()) {
                    val a = alertArr.getJSONObject(i)
                    alerts +=
                        FleetAlert(
                            id = a.optLong("id"),
                            kind = a.optString("kind"),
                            severity = a.optString("severity", "info"),
                            message = a.optString("message"),
                        )
                }
                HeartbeatResult(ota = ota, commands = cmds, alerts = alerts)
            }
        }

    fun ackCommands(ids: List<Long>, status: String = "acked"): Result<Unit> =
        runCatching {
            if (ids.isEmpty()) return@runCatching
            val arr = JSONArray()
            ids.forEach { arr.put(it) }
            val body =
                JSONObject()
                    .put("device_id", prefs.deviceId())
                    .put("command_ids", arr)
                    .put("status", status)
                    .toString()
                    .toRequestBody(JSON)
            val req =
                Request.Builder()
                    .url(base() + "/api/fleet/command/ack")
                    .post(body)
                    .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("ack HTTP ${resp.code}")
            }
        }

    companion object {
        private val JSON = "application/json".toMediaType()

        private fun mapToJson(map: Map<String, Any?>): JSONObject {
            val o = JSONObject()
            for ((k, v) in map) {
                when (v) {
                    null -> o.put(k, JSONObject.NULL)
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        o.put(k, mapToJson(v as Map<String, Any?>))
                    }
                    is Boolean, is Number, is String -> o.put(k, v)
                    else -> o.put(k, v.toString())
                }
            }
            return o
        }
    }
}
