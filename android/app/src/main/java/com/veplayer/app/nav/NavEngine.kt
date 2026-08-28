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

data class NavDestination(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
)

/**
 * Polls SenseFlow `/api/nav/route` (OSRM proxy) + destinations for native map.
 * Supports intermediate waypoints via prefs `nav_waypoints_json`.
 */
object NavEngine {
    private const val TAG = "NavEngine"
    private const val MAX_VIAS = 5

    private val _route = MutableStateFlow(NavRoute())
    val route: StateFlow<NavRoute> = _route.asStateFlow()

    private val _destinations = MutableStateFlow<List<NavDestination>>(emptyList())
    val destinations: StateFlow<List<NavDestination>> = _destinations.asStateFlow()

    private val _waypoints = MutableStateFlow<List<NavDestination>>(emptyList())
    val waypoints: StateFlow<List<NavDestination>> = _waypoints.asStateFlow()

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
        _waypoints.value = loadWaypoints(prefs)
        if (job?.isActive == true) return
        job =
            scope.launch(Dispatchers.IO) {
                refreshDestinations()
                while (isActive) {
                    refresh()
                    delay(15_000)
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun refreshAsync(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            refreshDestinations()
            refresh()
        }
    }

    /** Set final destination and clear intermediate vias. */
    fun setDestination(
        dest: NavDestination,
        scope: CoroutineScope? = null,
    ) {
        val p = prefs ?: return
        p.navEnabled = true
        p.navDestName = dest.name
        p.navToLat = dest.lat
        p.navToLng = dest.lng
        clearWaypoints(persist = true)
        if (scope != null) refreshAsync(scope) else refresh()
    }

    /** Append intermediate stop (before final dest). Max [MAX_VIAS]. */
    fun addWaypoint(
        dest: NavDestination,
        scope: CoroutineScope? = null,
    ) {
        val p = prefs ?: return
        val cur = loadWaypoints(p).toMutableList()
        // Don't duplicate final dest as via
        if (dest.name == p.navDestName &&
            kotlin.math.abs(dest.lat - p.navToLat) < 1e-5 &&
            kotlin.math.abs(dest.lng - p.navToLng) < 1e-5
        ) {
            return
        }
        if (cur.any { it.id == dest.id || (it.lat == dest.lat && it.lng == dest.lng) }) return
        if (cur.size >= MAX_VIAS) cur.removeAt(0)
        cur += dest
        saveWaypoints(p, cur)
        p.navEnabled = true
        if (scope != null) refreshAsync(scope) else refresh()
    }

    fun clearWaypoints(
        persist: Boolean = true,
        scope: CoroutineScope? = null,
    ) {
        val p = prefs
        _waypoints.value = emptyList()
        if (persist && p != null) p.navWaypointsJson = "[]"
        if (scope != null) refreshAsync(scope)
    }

    fun loadWaypoints(p: VePrefs? = null): List<NavDestination> {
        val prefs = p ?: this.prefs ?: return emptyList()
        val raw = prefs.navWaypointsJson
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        NavDestination(
                            id = o.optString("id").ifBlank { "via-$i" },
                            name = o.optString("name").ifBlank { "Parada ${i + 1}" },
                            lat = o.optDouble("lat"),
                            lng = o.optDouble("lng"),
                        ),
                    )
                }
            }
        }.getOrElse { emptyList() }.also { _waypoints.value = it }
    }

    private fun saveWaypoints(
        p: VePrefs,
        list: List<NavDestination>,
    ) {
        val arr = JSONArray()
        for (d in list.take(MAX_VIAS)) {
            arr.put(
                JSONObject()
                    .put("id", d.id)
                    .put("name", d.name)
                    .put("lat", d.lat)
                    .put("lng", d.lng),
            )
        }
        p.navWaypointsJson = arr.toString()
        _waypoints.value = list.take(MAX_VIAS)
    }

    fun refreshDestinations() {
        val p = prefs ?: return
        val url = p.senseflowUrl.trimEnd('/') + "/api/nav/destinations"
        runCatching {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("dest HTTP ${resp.code}")
                val arr = JSONObject(text).optJSONArray("destinations") ?: JSONArray()
                val list = mutableListOf<NavDestination>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list +=
                        NavDestination(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            lat = o.optDouble("lat"),
                            lng = o.optDouble("lng"),
                        )
                }
                if (list.isNotEmpty()) _destinations.value = list
            }
        }.onFailure { Log.w(TAG, "destinations fail", it) }
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
        val vias = loadWaypoints(p)
        var url =
            p.senseflowUrl.trimEnd('/') +
                "/api/nav/route?from_lat=$fromLat&from_lng=$fromLng&to_lat=$toLat&to_lng=$toLng" +
                "&dest_name=${java.net.URLEncoder.encode(name, "UTF-8")}"
        if (vias.isNotEmpty()) {
            val via = vias.joinToString(";") { "${it.lat},${it.lng}" }
            val viaNames =
                vias.joinToString(";") {
                    java.net.URLEncoder.encode(it.name, "UTF-8")
                }
            url += "&via=$via&via_names=$viaNames"
        }
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
                geom += c.getDouble(1) to c.getDouble(0)
            }
        }
        val wps = mutableListOf<NavWaypoint>()
        val wpArr = json.optJSONArray("waypoints")
        if (wpArr != null) {
            for (i in 0 until wpArr.length()) {
                val o = wpArr.getJSONObject(i)
                wps +=
                    NavWaypoint(
                        name = o.optString("name"),
                        lat = o.optDouble("lat"),
                        lng = o.optDouble("lng"),
                        role = o.optString("role", "via"),
                    )
            }
        }
        val legs = mutableListOf<NavLeg>()
        val legArr = json.optJSONArray("legs")
        if (legArr != null) {
            for (i in 0 until legArr.length()) {
                val o = legArr.getJSONObject(i)
                legs +=
                    NavLeg(
                        distanceM = o.optDouble("distance_m"),
                        durationS = o.optDouble("duration_s"),
                        toName = o.optString("to_name"),
                    )
            }
        }
        return NavRoute(
            distanceM = json.optDouble("distance_m"),
            durationS = json.optDouble("duration_s"),
            destinationName = json.optString("dest_name", fallbackName),
            steps = steps,
            geometry = geom,
            waypoints = wps,
            legs = legs,
            source = json.optString("source", "osrm"),
            updatedAtMs = System.currentTimeMillis(),
        )
    }
}
