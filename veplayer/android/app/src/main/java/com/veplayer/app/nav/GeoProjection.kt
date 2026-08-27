package com.veplayer.app.nav

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(
    val lat: Double,
    val lng: Double,
)

data class MapBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
) {
    fun padded(factor: Double = 0.12): MapBounds {
        val dLat = (maxLat - minLat).coerceAtLeast(0.002)
        val dLng = (maxLng - minLng).coerceAtLeast(0.002)
        return MapBounds(
            minLat = minLat - dLat * factor,
            maxLat = maxLat + dLat * factor,
            minLng = minLng - dLng * factor,
            maxLng = maxLng + dLng * factor,
        )
    }

    /** Expand by ~km in each direction (rough degrees). */
    fun paddedKm(km: Double): MapBounds {
        val dLat = km / 111.0
        val midLat = (minLat + maxLat) / 2.0
        val cosLat = kotlin.math.cos(Math.toRadians(midLat)).coerceAtLeast(0.2)
        val dLng = km / (111.0 * cosLat)
        return MapBounds(
            minLat = minLat - dLat,
            maxLat = maxLat + dLat,
            minLng = minLng - dLng,
            maxLng = maxLng + dLng,
        )
    }

    companion object {
        fun fromPoints(points: List<LatLng>): MapBounds? {
            if (points.isEmpty()) return null
            var minLat = points[0].lat
            var maxLat = points[0].lat
            var minLng = points[0].lng
            var maxLng = points[0].lng
            for (p in points) {
                minLat = min(minLat, p.lat)
                maxLat = max(maxLat, p.lat)
                minLng = min(minLng, p.lng)
                maxLng = max(maxLng, p.lng)
            }
            return MapBounds(minLat, maxLat, minLng, maxLng)
        }

        fun around(
            lat: Double,
            lng: Double,
            radiusKm: Double = 4.0,
        ): MapBounds = MapBounds(lat, lat, lng, lng).paddedKm(radiusKm)
    }
}

/** Project lat/lng into canvas pixels (equirectangular, north-up). */
object GeoProjection {
    fun project(
        lat: Double,
        lng: Double,
        bounds: MapBounds,
        width: Float,
        height: Float,
        paddingPx: Float = 28f,
    ): Pair<Float, Float> {
        val w = (width - paddingPx * 2).coerceAtLeast(1f)
        val h = (height - paddingPx * 2).coerceAtLeast(1f)
        val x = paddingPx + ((lng - bounds.minLng) / (bounds.maxLng - bounds.minLng).coerceAtLeast(1e-9) * w).toFloat()
        // lat grows north → y decreases
        val y = paddingPx + ((bounds.maxLat - lat) / (bounds.maxLat - bounds.minLat).coerceAtLeast(1e-9) * h).toFloat()
        return x to y
    }

    fun haversineM(
        a: LatLng,
        b: LatLng,
    ): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val la1 = Math.toRadians(a.lat)
        val la2 = Math.toRadians(b.lat)
        val h =
            sin(dLat / 2) * sin(dLat / 2) +
                cos(la1) * cos(la2) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    /**
     * Shortest distance (m) from [ego] to the polyline [path].
     * Uses local equirectangular projection per segment.
     */
    fun distanceToRouteM(
        path: List<LatLng>,
        ego: LatLng,
    ): Double {
        if (path.isEmpty()) return Double.POSITIVE_INFINITY
        if (path.size == 1) return haversineM(ego, path[0])
        var best = Double.MAX_VALUE
        for (i in 0 until path.lastIndex) {
            val d = distanceToSegmentM(ego, path[i], path[i + 1])
            if (d < best) best = d
        }
        return best
    }

    /** Distance (m) from point to segment AB. */
    fun distanceToSegmentM(
        p: LatLng,
        a: LatLng,
        b: LatLng,
    ): Double {
        val cosLat = cos(Math.toRadians(a.lat)).coerceAtLeast(1e-6)
        fun toXy(ll: LatLng): Pair<Double, Double> {
            val x = (ll.lng - a.lng) * 111_320.0 * cosLat
            val y = (ll.lat - a.lat) * 111_320.0
            return x to y
        }
        val (px, py) = toXy(p)
        val (bx, by) = toXy(b)
        val len2 = bx * bx + by * by
        if (len2 < 1e-4) return haversineM(p, a)
        val t = ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        val dx = px - t * bx
        val dy = py - t * by
        return sqrt(dx * dx + dy * dy)
    }

    /** Fraction 0..1 of progress along polyline toward destination. */
    fun progressAlong(
        path: List<LatLng>,
        ego: LatLng,
    ): Float {
        if (path.size < 2) return 0f
        var best = Double.MAX_VALUE
        var bestIdx = 0
        for (i in path.indices) {
            val d = haversineM(ego, path[i])
            if (d < best) {
                best = d
                bestIdx = i
            }
        }
        var total = 0.0
        var done = 0.0
        for (i in 0 until path.lastIndex) {
            val seg = haversineM(path[i], path[i + 1])
            total += seg
            if (i < bestIdx) done += seg
        }
        if (total <= 0) return 0f
        return (done / total).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Vehicle-frame offset (+y ahead, +x right) → geographic point.
     * [headingDeg] clockwise from north.
     */
    fun offsetToLatLng(
        ego: LatLng,
        headingDeg: Float,
        xM: Float,
        yM: Float,
    ): LatLng {
        val h = Math.toRadians(headingDeg.toDouble())
        val cosH = cos(h)
        val sinH = sin(h)
        val northM = yM * cosH - xM * sinH
        val eastM = yM * sinH + xM * cosH
        val dLat = northM / 111_320.0
        val cosLat = cos(Math.toRadians(ego.lat)).coerceAtLeast(1e-6)
        val dLng = eastM / (111_320.0 * cosLat)
        return LatLng(ego.lat + dLat, ego.lng + dLng)
    }
}
