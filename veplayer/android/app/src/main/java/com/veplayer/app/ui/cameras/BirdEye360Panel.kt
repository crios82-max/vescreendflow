package com.veplayer.app.ui.cameras

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veplayer.app.camera.BirdEyeCalibration
import com.veplayer.app.surround.ActorKind
import com.veplayer.app.surround.SurroundEngine
import com.veplayer.app.ui.theme.Mist
import com.veplayer.app.ui.theme.Mute
import com.veplayer.app.ui.theme.Night

/**
 * Bird's-eye composite: FOV wedges (pseudo-stitch) + live surround actors.
 */
@Composable
fun BirdEye360Panel(
    calibration: BirdEyeCalibration,
    modifier: Modifier = Modifier,
) {
    val surround by SurroundEngine.snapshot.collectAsState()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0A0A0A)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Road pad
            drawRoundRect(
                Color(0xFF1A1A1A),
                topLeft = Offset(w * 0.12f, h * 0.08f),
                size = Size(w * 0.76f, h * 0.84f),
                cornerRadius = CornerRadius(18f, 18f),
            )

            // Pseudo-stitch FOV wedges (front / rear / left / right)
            fun wedge(
                color: Color,
                tip: Offset,
                a: Offset,
                b: Offset,
            ) {
                val p =
                    Path().apply {
                        moveTo(tip.x, tip.y)
                        lineTo(a.x, a.y)
                        lineTo(b.x, b.y)
                        close()
                    }
                drawPath(p, color)
            }
            val cx = w * 0.5f
            val cy = h * 0.62f
            wedge(Color(0x223E9EFD), Offset(cx, cy), Offset(w * 0.18f, h * 0.1f), Offset(w * 0.82f, h * 0.1f))
            wedge(Color(0x2218C964), Offset(cx, cy), Offset(w * 0.2f, h * 0.9f), Offset(w * 0.8f, h * 0.9f))
            wedge(Color(0x22FFB74D), Offset(cx, cy), Offset(w * 0.08f, h * 0.25f), Offset(w * 0.08f, h * 0.85f))
            wedge(Color(0x22CE93D8), Offset(cx, cy), Offset(w * 0.92f, h * 0.25f), Offset(w * 0.92f, h * 0.85f))

            // Distance rings
            for (m in listOf(10f, 20f, 35f)) {
                val (nx, ny) = calibration.toNormalized(0f, m)
                val r = (1f - ny) * h * 0.55f
                drawCircle(
                    Color(0x33FFFFFF),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5f),
                )
            }

            // Actors
            for (actor in surround.actors.filter { it.yM > -5f && kotlin.math.abs(it.xM) < calibration.maxLatM }) {
                val (nx, ny) = calibration.toNormalized(actor.xM, actor.yM)
                val p = Offset(nx * w, ny * h)
                val color =
                    when (actor.kind) {
                        ActorKind.PERSON -> Color(0xFFFFB74D)
                        ActorKind.MOTORCYCLE, ActorKind.BICYCLE -> Color(0xFF80CBC4)
                        ActorKind.TRUCK, ActorKind.BUS -> Color(0xFF78909C)
                        ActorKind.CAR, ActorKind.UNKNOWN -> Color(0xFF9E9E9E)
                    }
                when (actor.kind) {
                    ActorKind.PERSON -> {
                        drawCircle(Color(0xFFFFCC80), radius = 7f, center = p)
                        drawRoundRect(
                            color,
                            topLeft = Offset(p.x - 4f, p.y - 18f),
                            size = Size(8f, 14f),
                            cornerRadius = CornerRadius(3f, 3f),
                        )
                    }
                    else -> {
                        val bw = if (actor.kind == ActorKind.TRUCK || actor.kind == ActorKind.BUS) w * 0.08f else w * 0.06f
                        val bh = if (actor.kind == ActorKind.TRUCK || actor.kind == ActorKind.BUS) h * 0.08f else h * 0.055f
                        drawRoundRect(
                            color,
                            topLeft = Offset(p.x - bw / 2, p.y - bh / 2),
                            size = Size(bw, bh),
                            cornerRadius = CornerRadius(8f, 8f),
                        )
                    }
                }
            }

            // Ego
            val egoW = w * 0.12f
            val egoH = h * 0.16f
            drawRoundRect(
                Color(0xFFE8E8E8),
                topLeft = Offset(cx - egoW / 2, cy - egoH * 0.35f),
                size = Size(egoW, egoH),
                cornerRadius = CornerRadius(14f, 14f),
            )
        }

        Text(
            "360 · bird’s-eye",
            color = Mist,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Night.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Text(
            "${surround.actors.size} actores · ${calibration.maxAheadM.toInt()}m",
            color = Mute,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        )
    }
}
