package com.veplayer.app.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veplayer.app.ui.theme.Card
import com.veplayer.app.ui.theme.Lane
import com.veplayer.app.ui.theme.Mist
import com.veplayer.app.ui.theme.Mute
import com.veplayer.app.ui.theme.Night
import com.veplayer.app.ui.theme.Road
import com.veplayer.app.vehicle.VehicleSnapshot

@Composable
fun DriveVizPanel(
    vehicle: VehicleSnapshot,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Night)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        // Speed
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (vehicle.reverse) "R" else vehicle.speedKmh.toInt().toString(),
                color = Mist,
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(Modifier = Modifier.width(8.dp))
            Text(
                if (vehicle.reverse) "REVERSE" else "km/h",
                color = Mute,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Text("Límite 50", color = Mute, fontSize = 13.sp)

        Spacer(Modifier = Modifier.height(8.dp))

        // 3D-ish road viz
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0A0A0A)),
        ) {
            RoadSceneCanvas(modifier = Modifier.fillMaxSize())
        }

        Spacer(Modifier = Modifier.height(10.dp))

        // Media widget (Tesla-style)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Card)
                .padding(14.dp),
        ) {
            Text("Euphoria - Single Version", color = Mist, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text("Loreen", color = Mute, fontSize = 13.sp)
            Spacer(Modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { 0.42f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Mist,
                trackColor = Color(0xFF333333),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = Mist)
                }
                IconButton(onClick = { }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Mist, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { }) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, tint = Mist)
                }
            }
        }
    }
}

@Composable
private fun RoadSceneCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // road
        drawRect(Road, topLeft = Offset(w * 0.18f, 0f), size = Size(w * 0.64f, h))
        // lane dashes
        var y = 20f
        while (y < h) {
            drawRoundRect(
                color = Lane,
                topLeft = Offset(w * 0.49f, y),
                size = Size(w * 0.02f, 28f),
                cornerRadius = CornerRadius(4f, 4f),
            )
            y += 56f
        }
        // ego car (white)
        val carW = w * 0.16f
        val carH = h * 0.22f
        drawRoundRect(
            color = Color(0xFFE8E8E8),
            topLeft = Offset(w * 0.42f, h * 0.55f),
            size = Size(carW, carH),
            cornerRadius = CornerRadius(18f, 18f),
        )
        // other vehicles (grey blocks)
        drawRoundRect(
            color = Color(0xFF6E6E6E),
            topLeft = Offset(w * 0.22f, h * 0.18f),
            size = Size(carW * 1.1f, carH * 0.9f),
            cornerRadius = CornerRadius(14f, 14f),
        )
        drawRoundRect(
            color = Color(0xFF7A7A7A),
            topLeft = Offset(w * 0.58f, h * 0.08f),
            size = Size(carW * 0.95f, carH * 0.85f),
            cornerRadius = CornerRadius(14f, 14f),
        )
        drawRoundRect(
            color = Color(0xFF5C5C5C),
            topLeft = Offset(w * 0.62f, h * 0.62f),
            size = Size(carW * 0.9f, carH * 0.8f),
            cornerRadius = CornerRadius(12f, 12f),
        )
    }
}
