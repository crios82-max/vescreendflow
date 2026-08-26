package com.veplayer.app.surround

import com.veplayer.app.data.VePrefs
import kotlin.math.cos
import kotlin.math.sin
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

    /**
     * @param headingDeg vehicle heading degrees clockwise from north; when set,
     *   rotates N/E offsets into vehicle frame (ahead = +y, right = +x).
     */
    fun fetch(
        lat: Double,
        lng: Double,
        radiusM: Int = 120,
        headingDeg: Float? = null,
    ): Result<List<SurroundActor>> =
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
                        var x = a.optDouble("x_m").toFloat()
                        var y = a.optDouble("y_m").toFloat()
                        if (headingDeg != null) {
                            val (xr, yr) = rotateToVehicle(x, y, headingDeg)
                            x = xr
                            y = yr
                        }
                        add(
                            SurroundActor(
                                id = "sf-" + a.optString("id", "$i"),
                                kind = kindOf(a.optString("kind")),
                                xM = x,
                                yM = y,
                                speedMps = a.optDouble("speed_mps").toFloat(),
                                source = "senseflow",
                            ),
                        )
                    }
                }
            }
        }

    /** Server x=east, y=north → vehicle x=right, y=ahead. */
    private fun rotateToVehicle(
        eastM: Float,
        northM: Float,
        headingDeg: Float,
    ): Pair<Float, Float> {
        val rad = Math.toRadians(headingDeg.toDouble())
        val cosH = cos(rad).toFloat()
        val sinH = sin(rad).toFloat()
        val ahead = northM * cosH + eastM * sinH
        val right = eastM * cosH - northM * sinH
        return right to ahead
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
