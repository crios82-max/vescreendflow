package com.senseflow.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PingPayload(
    val lat: Double,
    val lng: Double,
    val accuracyM: Float?,
    val speedMps: Float?,
    val activity: String,
    val deviceBucket: String,
    val ts: Long = System.currentTimeMillis() / 1000,
)

class ApiClient(private val baseUrlProvider: () -> String) {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    fun postPings(pings: List<PingPayload>): Result<Unit> =
        runCatching {
            val arr = JSONArray()
            for (p in pings) {
                arr.put(
                    JSONObject()
                        .put("lat", p.lat)
                        .put("lng", p.lng)
                        .put("accuracy_m", p.accuracyM?.toDouble())
                        .put("speed_mps", p.speedMps?.toDouble())
                        .put("activity", p.activity)
                        .put("device_bucket", p.deviceBucket)
                        .put("ts", p.ts),
                )
            }
            val body =
                JSONObject()
                    .put("pings", arr)
                    .toString()
                    .toRequestBody("application/json".toMediaType())
            val req =
                Request.Builder()
                    .url(baseUrlProvider().trimEnd('/') + "/api/pings")
                    .post(body)
                    .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.body?.string()}")
            }
        }
}
