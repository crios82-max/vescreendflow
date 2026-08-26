package com.veplayer.app.surround

import com.veplayer.app.data.VePrefs
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SenseflowSurroundClient(private val prefs: VePrefs) {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

    fun fetch(lat: Double, lng: Double, radiusM: Int = 120): Result<List<SurroundActor>> =
        runCatching {
            val url =
                prefs.senseflowUrl.trimEnd('/') +
                    "/api/surround?lat=$lat&lng=$lng&radius_m=$radiusM"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("surround HTTP ${resp.code}: $text")
                val json = JSONObject(text)
                val arr = json.optJSONArray("actors") ?: return@use emptyList()
                buildList {
                    for (i in 0 until arr.length()) {
                        val a = arr.getJSONObject(i)
                        add(
                            SurroundActor(
                                id = "sf-" + a.optString("id", "$i"),
                                kind = kindOf(a.optString("kind")),
                                xM = a.optDouble("x_m").toFloat(),
                                yM = a.optDouble("y_m").toFloat(),
                                speedMps = a.optDouble("speed_mps").toFloat(),
                                source = "senseflow",
                            ),
                        )
                    }
                }
            }
        }

    private fun kindOf(raw: String): ActorKind =
        when (raw.lowercase()) {
            "person" -> ActorKind.PERSON
            "motorcycle" -> ActorKind.MOTORCYCLE
            "bicycle" -> ActorKind.BICYCLE
            "car" -> ActorKind.CAR
            "truck" -> ActorKind.TRUCK
            "bus" -> ActorKind.BUS
            else -> ActorKind.UNKNOWN
        }
}
