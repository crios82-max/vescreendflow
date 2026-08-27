package com.veplayer.app.ui.cameras

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veplayer.app.vehicle.ParkingDistance

/**
 * Three vertical PDC bars (L/C/R) + distance label for reverse camera.
 */
@Composable
fun ParkingDistanceOverlay(
    state: ParkingDistance.State,
    modifier: Modifier = Modifier,
) {
    if (!state.active) return
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val barW = w * 0.06f
            val barH = h * 0.42f
            val baseY = h * 0.92f
            val gap = w * 0.08f
            val centers =
                listOf(
                    w * 0.5f - gap - barW,
                    w * 0.5f - barW / 2f,
                    w * 0.5f + gap,
                )
            val fills =
                listOf(
                    ParkingDistance.barFill(state.zones.rearL),
                    ParkingDistance.barFill(state.zones.rearC),
                    ParkingDistance.barFill(state.zones.rearR),
                )
            val accent = Color(ParkingDistance.accentArgb(state.band))
            centers.forEachIndexed { i, left ->
                val fill = fills[i]
                // track
                drawRoundRect(
                    color = Color(0x66000000),
                    topLeft = Offset(left, baseY - barH),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(6f, 6f),
                )
                if (fill > 0.02f) {
                    val fh = barH * fill
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(left, baseY - fh),
                        size = Size(barW, fh),
                        cornerRadius = CornerRadius(6f, 6f),
                    )
                }
            }
        }
        if (state.label.isNotBlank()) {
            Text(
                state.label,
                color = Color(ParkingDistance.accentArgb(state.band)),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp),
            )
        }
    }
}
