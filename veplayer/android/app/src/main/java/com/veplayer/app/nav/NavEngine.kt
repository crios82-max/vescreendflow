package com.veplayer.app.nav

import android.util.Log
import com.veplayer.app.data.VePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Polls SenseFlow `/api/nav/route` (OSRM proxy) and publishes [NavRoute] for cockpit chrome.
 */
object NavEngine {
    private const val TAG = "NavEngine"

    private val _route = MutableStateFlow(NavRoute())
    val route: StateFlow<NavRoute> = _route.asStateFlow()

    private var prefs: VePrefs? = null
    private var job: Job? = null
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()

    fun start(
        prefs: VePrefs,
        scope: CoroutineScope,
    ) {
        this.prefs = prefs
        if (job?.isActive == true) return
        job =
            scope.launch(Dispatchers.IO) {
                while (isActive) {
                    refresh()
                    delay(20_000)
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun refreshAsync(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) { refresh() }
    }

    fun refresh() {
        val p = prefs ?: return
        if (!p.navEnabled) {
            _route.value = NavRoute(destinationName = "Nav off", source = "off")
            return
        }
        val fromLat = p.navFromLat
        val fromLng = p.navFromLng
        val toLat = p.navToLat
        val toLng = p.navToLng
        val name = p.navDestName
        val url =
            p.senseflowUrl.trimEnd('/') +
                "/api/nav/route?from_lat=$fromLat&from_lng=$fromLng&to_lat=$toLat&to_lng=$toLng" +
                "&dest_name=${java.net.URLEncoder.encode(name, "UTF-8")}"
        runCatching {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("nav HTTP ${resp.code}: $text")
                _route.value = parse(JSONObject(text), name)
            }
        }.onFailure {
            Log.w(TAG, "nav refresh failed", it)
            _route.value =
                _route.value.copy(
                    source = "error",
                    destinationName = name,
                    updatedAtMs = System.currentTimeMillis(),
                )
        }
    }

    private fun parse(
        json: JSONObject,
        fallbackName: String,
    ): NavRoute {
        val stepsArr = json.optJSONArray("steps") ?: JSONArray()
        val steps = mutableListOf<NavStep>()
        for (i in 0 until stepsArr.length()) {
            val s = stepsArr.getJSONObject(i)
            steps +=
                NavStep(
                    instruction = s.optString("instruction", "Continuar"),
                    distanceM = s.optDouble("distance_m").toFloat(),
                    name = s.optString("name", ""),
                    type = s.optString("type", ""),
                    modifier = s.optString("modifier", ""),
                )
        }
        val geom = mutableListOf<Pair<Double, Double>>()
        val g = json.optJSONObject("geometry")
        val coords = g?.optJSONArray("coordinates")
        if (coords != null) {
            for (i in 0 until coords.length()) {
                val c = coords.getJSONArray(i)
                // GeoJSON lon,lat → lat,lng
                geom += c.getDouble(1) to c.getDouble(0)
            }
        }
        return NavRoute(
            distanceM = json.optDouble("distance_m"),
            durationS = json.optDouble("duration_s"),
            destinationName = json.optString("dest_name", fallbackName),
            steps = steps,
            geometry = geom,
            source = json.optString("source", "osrm"),
            updatedAtMs = System.currentTimeMillis(),
        )
    }
}
