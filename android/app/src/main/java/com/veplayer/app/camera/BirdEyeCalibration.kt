package com.veplayer.app.camera

/**
 * Bird's-eye calibration: image/world plane → meters relative to ego.
 * Default matches DriveViz (maxAhead / maxLat). Tune in Cámaras → 360.
 */
data class BirdEyeCalibration(
    /** meters ahead visible in bird-eye */
    val maxAheadM: Float = 50f,
    /** meters left/right (±) */
    val maxLatM: Float = 18f,
    /** ego car length in meters (visual) */
    val egoLengthM: Float = 4.5f,
    val egoWidthM: Float = 1.9f,
) {
    /** Normalize actor meters → 0..1 canvas (x right, y ahead→up). */
    fun toNormalized(
        xM: Float,
        yM: Float,
    ): Pair<Float, Float> {
        val nx = ((xM / maxLatM) * 0.5f + 0.5f).coerceIn(0.02f, 0.98f)
        val ny = (1f - (yM / maxAheadM)).coerceIn(0.02f, 0.95f)
        return nx to ny
    }
}
