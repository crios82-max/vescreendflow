package com.veplayer.app.ui.map

import android.content.Context
import com.veplayer.app.data.VePrefs
import com.veplayer.app.nav.LatLng
import com.veplayer.app.nav.MapBounds
import com.veplayer.app.nav.NavEngine
import com.veplayer.app.nav.WebMercator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Offline OSM tile prefetch (around ego or active route corridor).
 */
object OsmPrefetch {
    data class State(
        val running: Boolean = false,
        val label: String = "Idle",
        val done: Int = 0,
        val total: Int = 0,
        val downloaded: Int = 0,
        val skipped: Int = 0,
        val failed: Int = 0,
        val cacheMb: Double = 0.0,
        val cacheFiles: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    fun refreshStats(context: Context) {
        val bytes = OsmTileStore.cacheBytes(context)
        _state.value =
            _state.value.copy(
                cacheMb = bytes / (1024.0 * 1024.0),
                cacheFiles = OsmTileStore.cacheFileCount(context),
            )
    }

    fun clear(context: Context) {
        job?.cancel()
        val freed = OsmTileStore.clearCache(context)
        _state.value =
            State(
                label = "Caché borrada · ${"%.1f".format(freed / (1024.0 * 1024.0))} MB",
                cacheMb = 0.0,
                cacheFiles = 0,
            )
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = _state.value.copy(running = false, label = "Cancelado")
    }

    fun startAroundMe(
        context: Context,
        prefs: VePrefs,
        scope: CoroutineScope,
        radiusKm: Double = 4.0,
    ) {
        val lat = prefs.navFromLat.takeIf { it != 0.0 } ?: 10.496
        val lng = prefs.navFromLng.takeIf { it != 0.0 } ?: -66.898
        val bounds = MapBounds.around(lat, lng, radiusKm)
        start(context, prefs, scope, bounds, "Alrededor · ${radiusKm.toInt()} km")
    }

    fun startRoute(
        context: Context,
        prefs: VePrefs,
        scope: CoroutineScope,
        corridorKm: Double = 1.0,
    ) {
        val route = NavEngine.route.value
        val pts = route.geometry.map { LatLng(it.first, it.second) }
        val bounds =
            if (pts.size >= 2) {
                MapBounds.fromPoints(pts)?.paddedKm(corridorKm)
            } else {
                null
            }
        if (bounds == null) {
            startAroundMe(context, prefs, scope)
            return
        }
        start(context, prefs, scope, bounds, "Ruta · corredor ${corridorKm} km")
    }

    fun start(
        context: Context,
        prefs: VePrefs,
        scope: CoroutineScope,
        bounds: MapBounds,
        label: String,
    ) {
        if (job?.isActive == true) return
        val zMin = prefs.mapPrefetchZMin
        val zMax = prefs.mapPrefetchZMax
        val keys =
            WebMercator.tilesForBoundsRange(
                bounds = bounds,
                zMin = zMin,
                zMax = zMax,
                maxTiles = prefs.mapPrefetchMaxTiles,
            )
        if (keys.isEmpty()) {
            _state.value = _state.value.copy(label = "Sin tiles en rango")
            return
        }
        job =
            scope.launch {
                _state.value =
                    State(
                        running = true,
                        label = "$label · z$zMin–$zMax · ${keys.size} tiles",
                        total = keys.size,
                    )
                val result =
                    OsmTileStore.prefetch(
                        context = context,
                        keys = keys,
                        template = prefs.mapTileUrl,
                        paceMs = 70L,
                    ) { done, total, ok ->
                        _state.value =
                            _state.value.copy(
                                done = done,
                                total = total,
                                downloaded = ok,
                                label = "$label · $done/$total",
                            )
                    }
                refreshStats(context)
                _state.value =
                    _state.value.copy(
                        running = false,
                        downloaded = result.downloaded,
                        skipped = result.skipped,
                        failed = result.failed,
                        done = result.total,
                        total = result.total,
                        label =
                            "Listo · ↓${result.downloaded} · skip ${result.skipped} · fail ${result.failed}",
                    )
            }
    }

    suspend fun planTileCount(
        bounds: MapBounds,
        zMin: Int,
        zMax: Int,
        maxTiles: Int,
    ): Int =
        withContext(Dispatchers.Default) {
            WebMercator.tilesForBoundsRange(bounds, zMin, zMax, maxTiles).size
        }
}
