package com.veplayer.app.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veplayer.app.vehicle.SpeedHud

/** Circular speed-limit badge (EU-style). */
@Composable
fun SpeedLimitBadge(
    limitKmh: Int,
    band: String,
    modifier: Modifier = Modifier,
) {
    val accent = Color(SpeedHud.accentArgb(band))
    Box(
        modifier = modifier.size(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(56.dp)) {
            val r = size.minDimension / 2f
            drawCircle(Color(0xFFF8FAFC), radius = r)
            drawCircle(
                Color(0xFFE11D48),
                radius = r,
                style = Stroke(width = 5.5f),
            )
            drawCircle(
                accent.copy(alpha = if (band == "over") 0.35f else 0.12f),
                radius = r * 0.72f,
            )
            // subtle tick
            drawCircle(Color(0x22000000), radius = 2f, center = Offset(size.width * 0.5f, 8f))
        }
        Text(
            "$limitKmh",
            color = Color(0xFF0F172A),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
