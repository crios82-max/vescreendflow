package com.veplayer.app.fleet

import com.veplayer.app.BuildConfig
import com.veplayer.app.data.VePrefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class OtaInfo(
    val updateAvailable: Boolean,
    val latestVersionName: String?,
    val latestVersionCode: Int?,
    val apkUrl: String?,
    val notes: String?,
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
    ): Result<OtaInfo?> =
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
            val req =
                Request.Builder()
                    .url(base() + "/api/fleet/heartbeat")
                    .post(payload.toString().toRequestBody(JSON))
                    .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("heartbeat HTTP ${resp.code}: $text")
                val json = JSONObject(text)
                val ota = json.optJSONObject("ota") ?: return@use null
                OtaInfo(
                    updateAvailable = ota.optBoolean("update_available"),
                    latestVersionName = ota.optString("latest_version_name", null),
                    latestVersionCode = if (ota.has("latest_version_code")) ota.getInt("latest_version_code") else null,
                    apkUrl = ota.optString("apk_url", null),
                    notes = ota.optString("notes", null),
                )
            }
        }

    companion object {
        private val JSON = "application/json".toMediaType()
    }
}
