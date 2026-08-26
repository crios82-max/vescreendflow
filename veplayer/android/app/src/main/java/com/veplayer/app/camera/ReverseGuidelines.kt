package com.veplayer.app.camera

/**
 * Reverse-camera parking guidelines in normalized coords (0..1, origin top-left).
 * Bottom of frame ≈ bumper; positive [steeringDeg] bends path to the left of screen
 * (as seen looking rearward).
 */
object ReverseGuidelines {
    data class Point(
        val x: Float,
        val y: Float,
    )

    data class Band(
        /** 0..1 along guide depth (0 = bumper, 1 = far). */
        val t: Float,
        val colorArgb: Long,
        val labelM: Int,
    )

    data class GuideSet(
        val left: List<Point>,
        val right: List<Point>,
        val center: List<Point>,
        val bands: List<Pair<Point, Point>>, // left↔right connectors at each band
        val bandMeta: List<Band>,
        val bumper: Pair<Point, Point>,
    )

    val DEFAULT_BANDS =
        listOf(
            Band(0.22f, 0xE0EF4444, 1), // red ~1 m
            Band(0.45f, 0xE0F59E0B, 2), // amber
            Band(0.72f, 0xE022C55E, 4), // green
        )

    /**
     * @param steeringDeg wheel angle (−45..45); null → straight
     * @param trackWidth fraction of frame width between rails at bumper
     * @param farWidth fraction at far end (perspective taper)
     * @param depth how far up the frame the guides reach (0.5..0.95)
     * @param curveGain how strongly steering bends (screen units)
     */
    fun compute(
        steeringDeg: Float? = null,
        trackWidth: Float = 0.46f,
        farWidth: Float = 0.22f,
        depth: Float = 0.78f,
        segments: Int = 28,
        curveGain: Float = 0.28f,
        bands: List<Band> = DEFAULT_BANDS,
    ): GuideSet {
        val steer = (steeringDeg ?: 0f).coerceIn(-45f, 45f) / 45f
        val halfNear = (trackWidth / 2f).coerceIn(0.12f, 0.4f)
        val halfFar = (farWidth / 2f).coerceIn(0.05f, halfNear)
        val d = depth.coerceIn(0.45f, 0.92f)
        val n = segments.coerceIn(8, 64)

        fun sample(side: Float): List<Point> {
            val pts = ArrayList<Point>(n + 1)
            for (i in 0..n) {
                val t = i.toFloat() / n
                val y = 1f - t * d
                val half = halfNear + (halfFar - halfNear) * t
                // Quadratic bend grows with distance (rearward arc)
                val bend = steer * curveGain * t * t
                val x = 0.5f + side * half + bend
                pts += Point(x.coerceIn(0.02f, 0.98f), y.coerceIn(0.02f, 0.98f))
            }
            return pts
        }

        val left = sample(-1f)
        val right = sample(1f)
        val center = sample(0f).map { it.copy(x = it.x) }

        val bandSegs = mutableListOf<Pair<Point, Point>>()
        for (b in bands) {
            val t = b.t.coerceIn(0f, 1f)
            val y = 1f - t * d
            val half = halfNear + (halfFar - halfNear) * t
            val bend = steer * curveGain * t * t
            bandSegs +=
                Point((0.5f - half + bend).coerceIn(0.02f, 0.98f), y) to
                    Point((0.5f + half + bend).coerceIn(0.02f, 0.98f), y)
        }

        val bumperY = 0.97f
        val bumper =
            Point(0.5f - halfNear * 1.05f, bumperY) to Point(0.5f + halfNear * 1.05f, bumperY)

        return GuideSet(
            left = left,
            right = right,
            center = center,
            bands = bandSegs,
            bandMeta = bands,
            bumper = bumper,
        )
    }

    /** Approximate lateral offset at far end for tests / HUD. */
    fun farBendNorm(
        steeringDeg: Float,
        curveGain: Float = 0.28f,
    ): Float = (steeringDeg.coerceIn(-45f, 45f) / 45f) * curveGain
}
