package com.veplayer.app.nav

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sinh

/** OSM slippy-map tile key (z/x/y). */
data class TileKey(
    val z: Int,
    val x: Int,
    val y: Int,
)

/**
 * Web Mercator helpers for aligning OSM raster tiles with route overlay.
 * World pixel space at zoom z: [0, 256 * 2^z).
 */
object WebMercator {
    const val TILE_SIZE = 256

    fun latLngToWorld(
        lat: Double,
        lng: Double,
        zoom: Int,
    ): Pair<Double, Double> {
        val z = zoom.coerceIn(0, 22)
        val scale = TILE_SIZE * 2.0.pow(z)
        val x = ((lng + 180.0) / 360.0) * scale
        val latClamped = lat.coerceIn(-85.05112878, 85.05112878)
        val sinLat = sinRad(latClamped)
        val y = (0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)) * scale
        return x to y
    }

    fun worldToLatLng(
        wx: Double,
        wy: Double,
        zoom: Int,
    ): LatLng {
        val scale = TILE_SIZE * 2.0.pow(zoom.coerceIn(0, 22))
        val lng = wx / scale * 360.0 - 180.0
        val n = PI - 2.0 * PI * wy / scale
        val lat = Math.toDegrees(atan(sinh(n)))
        return LatLng(lat, lng)
    }

    /** Largest zoom where [bounds] fits in [width]×[height] with padding (tiles ≈ 1:1). */
    fun zoomForBounds(
        bounds: MapBounds,
        width: Float,
        height: Float,
        paddingPx: Float = 24f,
    ): Int {
        val availW = (width - paddingPx * 2).coerceAtLeast(64f)
        val availH = (height - paddingPx * 2).coerceAtLeast(64f)
        for (z in 18 downTo 2) {
            val (minX, minY) = latLngToWorld(bounds.maxLat, bounds.minLng, z)
            val (maxX, maxY) = latLngToWorld(bounds.minLat, bounds.maxLng, z)
            val bw = (maxX - minX).coerceAtLeast(1.0)
            val bh = (maxY - minY).coerceAtLeast(1.0)
            if (bw <= availW && bh <= availH) return z
        }
        return 2
    }

    fun viewportFor(
        bounds: MapBounds,
        width: Float,
        height: Float,
        paddingPx: Float = 24f,
        zoom: Int = zoomForBounds(bounds, width, height, paddingPx),
    ): MapViewport {
        val (minX, minY) = latLngToWorld(bounds.maxLat, bounds.minLng, zoom)
        val (maxX, maxY) = latLngToWorld(bounds.minLat, bounds.maxLng, zoom)
        val bw = (maxX - minX).coerceAtLeast(1.0)
        val bh = (maxY - minY).coerceAtLeast(1.0)
        val availW = (width - paddingPx * 2).coerceAtLeast(1f).toDouble()
        val availH = (height - paddingPx * 2).coerceAtLeast(1f).toDouble()
        val scale = min(availW / bw, availH / bh)
        val usedW = bw * scale
        val usedH = bh * scale
        val originX = minX - (availW - usedW) / (2.0 * scale) - paddingPx / scale
        val originY = minY - (availH - usedH) / (2.0 * scale) - paddingPx / scale
        return MapViewport(zoom = zoom, originWorldX = originX, originWorldY = originY, scale = scale)
    }

    fun project(
        lat: Double,
        lng: Double,
        vp: MapViewport,
    ): Pair<Float, Float> {
        val (wx, wy) = latLngToWorld(lat, lng, vp.zoom)
        val x = ((wx - vp.originWorldX) * vp.scale).toFloat()
        val y = ((wy - vp.originWorldY) * vp.scale).toFloat()
        return x to y
    }

    fun tilesCovering(
        vp: MapViewport,
        width: Float,
        height: Float,
    ): List<TileKey> {
        val z = vp.zoom
        val n = 1 shl z
        val left = vp.originWorldX
        val top = vp.originWorldY
        val right = left + width / vp.scale
        val bottom = top + height / vp.scale
        val x0 = floor(left / TILE_SIZE).toInt().coerceIn(0, n - 1)
        val x1 = floor((right - 1e-6) / TILE_SIZE).toInt().coerceIn(0, n - 1)
        val y0 = floor(top / TILE_SIZE).toInt().coerceIn(0, n - 1)
        val y1 = floor((bottom - 1e-6) / TILE_SIZE).toInt().coerceIn(0, n - 1)
        val out = ArrayList<TileKey>((x1 - x0 + 1) * (y1 - y0 + 1))
        for (x in x0..x1) {
            for (y in y0..y1) {
                out.add(TileKey(z, x, y))
            }
        }
        return out
    }

    /** All slippy tiles covering [bounds] at a single zoom. */
    fun tilesForBounds(
        bounds: MapBounds,
        zoom: Int,
    ): List<TileKey> {
        val z = zoom.coerceIn(0, 22)
        val n = 1 shl z
        val (wx0, wy0) = latLngToWorld(bounds.maxLat, bounds.minLng, z)
        val (wx1, wy1) = latLngToWorld(bounds.minLat, bounds.maxLng, z)
        val x0 = floor(wx0 / TILE_SIZE).toInt().coerceIn(0, n - 1)
        val x1 = floor((wx1 - 1e-9) / TILE_SIZE).toInt().coerceIn(0, n - 1)
        val y0 = floor(wy0 / TILE_SIZE).toInt().coerceIn(0, n - 1)
        val y1 = floor((wy1 - 1e-9) / TILE_SIZE).toInt().coerceIn(0, n - 1)
        val out = ArrayList<TileKey>((x1 - x0 + 1) * (y1 - y0 + 1))
        for (x in x0..x1) {
            for (y in y0..y1) {
                out.add(TileKey(z, x, y))
            }
        }
        return out
    }

    /** Union of tiles for zoom range (inclusive), capped at [maxTiles]. */
    fun tilesForBoundsRange(
        bounds: MapBounds,
        zMin: Int,
        zMax: Int,
        maxTiles: Int = 2500,
    ): List<TileKey> {
        val lo = zMin.coerceIn(0, 22)
        val hi = zMax.coerceIn(lo, 22)
        val out = ArrayList<TileKey>()
        for (z in lo..hi) {
            val batch = tilesForBounds(bounds, z)
            if (out.size + batch.size > maxTiles) {
                out.addAll(batch.take(maxTiles - out.size))
                break
            }
            out.addAll(batch)
        }
        return out
    }

    fun tileScreenRect(
        key: TileKey,
        vp: MapViewport,
    ): TileScreenRect {
        val wx = key.x * TILE_SIZE.toDouble()
        val wy = key.y * TILE_SIZE.toDouble()
        val left = ((wx - vp.originWorldX) * vp.scale).toFloat()
        val top = ((wy - vp.originWorldY) * vp.scale).toFloat()
        val size = (TILE_SIZE * vp.scale).toFloat()
        return TileScreenRect(left, top, size, size)
    }

    private fun sinRad(latDeg: Double): Double = kotlin.math.sin(Math.toRadians(latDeg))
}

data class MapViewport(
    val zoom: Int,
    val originWorldX: Double,
    val originWorldY: Double,
    val scale: Double,
)

data class TileScreenRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)
