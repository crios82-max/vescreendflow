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
    val shiftJson: JSONObject? = null,
    val panicOpen: Boolean = false,
    val panicAlertId: Long? = null,
    val panicMessage: String? = null,
    val panicClipUrl: String? = null,
    val speedZoneId: Int? = null,
    val speedZoneName: String? = null,
    val speedZoneMaxKmh: Int? = null,
)

data class PanicResult(
    val alertId: Long?,
    val message: String,
    val deduped: Boolean = false,
    val clipUrl: String? = null,
)

data class PanicClipResult(
    val clipUrl: String?,
    val bytes: Int,
    val sim: Boolean,
    val alertId: Long?,
)

data class IncidentResult(
    val alertId: Long?,
    val message: String,
    val category: String,
    val deduped: Boolean = false,
    val clipUrl: String? = null,
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
            val odo = vehicleSignals?.get("odometer_km") as? Number
            if (odo != null) payload.put("odo_km", odo.toDouble())
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
                val panicJson = json.optJSONObject("panic")
                val zoneJson = json.optJSONObject("speed_zone")
                HeartbeatResult(
                    ota = ota,
                    commands = cmds,
                    alerts = alerts,
                    shiftJson = json.optJSONObject("shift"),
                    panicOpen = panicJson?.optBoolean("open") == true,
                    panicAlertId =
                        if (panicJson != null && panicJson.has("id")) panicJson.optLong("id") else null,
                    panicMessage = panicJson?.optString("message"),
                    panicClipUrl = panicJson?.optString("clip_url")?.takeIf { it.isNotBlank() },
                    speedZoneId =
                        if (zoneJson != null && zoneJson.has("id")) zoneJson.optInt("id") else null,
                    speedZoneName = zoneJson?.optString("name"),
                    speedZoneMaxKmh =
                        if (zoneJson != null && zoneJson.has("max_kmh")) zoneJson.optInt("max_kmh")
                        else null,
                )
            }
        }

    fun panic(
        lat: Double? = null,
        lng: Double? = null,
        note: String? = null,
        driverCode: String? = null,
        driverName: String? = null,
    ): Result<PanicResult> =
        runCatching {
            val payload =
                JSONObject()
                    .put("device_id", prefs.deviceId())
                    .put("source", "veplayer")
            if (lat != null) payload.put("lat", lat)
            if (lng != null) payload.put("lng", lng)
            if (!note.isNullOrBlank()) payload.put("note", note.trim())
            if (!driverCode.isNullOrBlank()) payload.put("driver_code", driverCode)
            if (!driverName.isNullOrBlank()) payload.put("driver_name", driverName)
            val req =
                Request.Builder()
                    .url(base() + "/api/fleet/panic")
                    .post(payload.toString().toRequestBody(JSON))
                    .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("panic HTTP ${resp.code}: $text")
                val json = JSONObject(text)
                val alert = json.optJSONObject("alert")
                PanicResult(
                    alertId = alert?.optLong("id"),
                    message = alert?.optString("message") ?: "SOS enviado",
                    deduped = json.optBoolean("deduped"),
                    clipUrl = alert?.optString("clip_url")?.takeIf { it.isNotBlank() },
                )
            }
        }

    fun uploadPanicClip(
        alertId: Long?,
        jpegBytes: ByteArray,
        camera: String = "front",
        durationSec: Int = 8,
        sim: Boolean = true,
    ): Result<PanicClipResult> =
        runCatching {
            val b64 = android.util.Base64.encodeToString(jpegBytes, android.util.Base64.NO_WRAP)
            val payload =
                JSONObject()
                    .put("device_id", prefs.deviceId())
                    .put("kind", "jpeg")
                    .put("data_base64", b64)
                    .put("camera", camera)
                    .put("duration_sec", durationSec)
                    .put("sim", sim)
                    .put("captured_at_ms", System.currentTimeMillis())
            if (alertId != null && alertId > 0) payload.put("alert_id", alertId)
            val req =
                Request.Builder()
                    .url(base() + "/api/fleet/panic/clip")
                    .post(payload.toString().toRequestBody(JSON))
                    .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("panic clip HTTP ${resp.code}: $text")
                val json = JSONObject(text)
                PanicClipResult(
                    clipUrl = json.optString("clip_url").takeIf { it.isNotBlank() },
                    bytes = json.optInt("bytes", jpegBytes.size),
                    sim = json.optBoolean("sim", sim),
                    alertId = if (json.has("alert_id")) json.optLong("alert_id") else alertId,
                )
            }
        }

    fun incident(
        category: String = "other",
        note: String? = null,
        lat: Double? = null,
        lng: Double? = null,
        clipJpeg: ByteArray? = null,
        clipSim: Boolean = true,
        driverCode: String? = null,
        driverName: String? = null,
    ): Result<IncidentResult> =
        runCatching {
            val payload =
                JSONObject()
                    .put("device_id", prefs.deviceId())
                    .put("category", category)
                    .put("source", "veplayer")
            if (!note.isNullOrBlank()) payload.put("note", note.trim().take(280))
            if (lat != null) payload.put("lat", lat)
            if (lng != null) payload.put("lng", lng)
            if (!driverCode.isNullOrBlank()) payload.put("driver_code", driverCode)
            if (!driverName.isNullOrBlank()) payload.put("driver_name", driverName)
            if (clipJpeg != null && clipJpeg.isNotEmpty()) {
                payload.put("clip_kind", "jpeg")
                payload.put(
                    "clip_base64",
                    android.util.Base64.encodeToString(clipJpeg, android.util.Base64.NO_WRAP),
                )
                payload.put("clip_sim", clipSim)
            }
            val req =
                Request.Builder()
                    .url(base() + "/api/fleet/incident")
                    .post(payload.toString().toRequestBody(JSON))
                    .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("incident HTTP ${resp.code}: $text")
                val json = JSONObject(text)
                val alert = json.optJSONObject("alert")
                IncidentResult(
                    alertId = alert?.optLong("id"),
                    message = alert?.optString("message") ?: "Incidente enviado",
                    category = category,
                    deduped = json.optBoolean("deduped"),
                    clipUrl = alert?.optString("clip_url")?.takeIf { it.isNotBlank() },
                )
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

    fun ackMessage(alertId: Long): Result<Unit> =
        runCatching {
            val body =
                JSONObject()
                    .put("device_id", prefs.deviceId())
                    .put("alert_id", alertId)
                    .toString()
                    .toRequestBody(JSON)
            val req =
                Request.Builder()
                    .url(base() + "/api/fleet/message/ack")
                    .post(body)
                    .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("message ack HTTP ${resp.code}: $text")
            }
        }

    fun replyMessage(
        text: String? = null,
        canned: String? = null,
        alertId: Long? = null,
    ): Result<String> =
        runCatching {
            val body = JSONObject().put("device_id", prefs.deviceId())
            if (alertId != null && alertId > 0) body.put("alert_id", alertId)
            if (!text.isNullOrBlank()) body.put("text", text.trim().take(500))
            if (!canned.isNullOrBlank()) body.put("canned", canned.trim())
            val req =
                Request.Builder()
                    .url(base() + "/api/fleet/message/reply")
                    .post(body.toString().toRequestBody(JSON))
                    .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("message reply HTTP ${resp.code}: $raw")
                val json = JSONObject(raw)
                json.optJSONObject("reply")?.optString("message")
                    ?: text?.trim()
                    ?: canned
                    ?: "OK"
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
