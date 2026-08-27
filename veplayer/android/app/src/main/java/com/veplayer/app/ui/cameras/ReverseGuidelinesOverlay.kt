package com.veplayer.app.ui.cameras

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.veplayer.app.camera.ReverseGuidelines

/**
 * Parking guide overlay for reverse camera preview (Compose Canvas).
 */
@Composable
fun ReverseGuidelinesOverlay(
    steeringDeg: Float?,
    trackWidth: Float = 0.46f,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    val guides =
        remember(steeringDeg, trackWidth) {
            ReverseGuidelines.compute(
                steeringDeg = steeringDeg,
                trackWidth = trackWidth,
            )
        }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        fun pt(p: ReverseGuidelines.Point) = Offset(p.x * w, p.y * h)

        fun rail(
            points: List<ReverseGuidelines.Point>,
            color: Color,
            widthPx: Float,
        ) {
            if (points.size < 2) return
            val path =
                Path().apply {
                    val first = pt(points.first())
                    moveTo(first.x, first.y)
                    for (i in 1 until points.size) {
                        val o = pt(points[i])
                        lineTo(o.x, o.y)
                    }
                }
            drawPath(
                path,
                color,
                style =
                    Stroke(
                        width = widthPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
            )
        }

        // Soft glow
        rail(guides.left, Color(0x55FFFFFF), 10f)
        rail(guides.right, Color(0x55FFFFFF), 10f)
        rail(guides.left, Color(0xFFE8F5E9), 3.5f)
        rail(guides.right, Color(0xFFE8F5E9), 3.5f)
        rail(guides.center, Color(0x66FFFFFF), 1.5f)

        guides.bands.forEachIndexed { i, (a, b) ->
            val meta = guides.bandMeta.getOrNull(i)
            val color = Color(meta?.colorArgb ?: 0xE0FFFFFF)
            drawLine(
                color,
                pt(a),
                pt(b),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
        }

        drawLine(
            Color(0xCCFFFFFF),
            pt(guides.bumper.first),
            pt(guides.bumper.second),
            strokeWidth = 5f,
            cap = StrokeCap.Round,
        )
    }
}
