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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.veplayer.app.surround.ActorKind
import com.veplayer.app.surround.SurroundActor
import com.veplayer.app.surround.SurroundEngine
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
    val surround by SurroundEngine.snapshot.collectAsState()

    Column(
        modifier = modifier
            .background(Night)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (vehicle.reverse) "R" else vehicle.speedKmh.toInt().toString(),
                color = Mist,
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (vehicle.reverse) "REVERSE" else "km/h",
                color = Mute,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        val counts =
            surround.actors.groupingBy { it.kind }.eachCount()
        Text(
            buildString {
                append("Límite 50")
                if (surround.actors.isNotEmpty()) {
                    append(" · ")
                    append(counts[ActorKind.PERSON] ?: 0)
                    append(" personas · ")
                    append((counts[ActorKind.MOTORCYCLE] ?: 0) + (counts[ActorKind.BICYCLE] ?: 0))
                    append(" motos/bici · ")
                    append(
                        (counts[ActorKind.CAR] ?: 0) +
                            (counts[ActorKind.TRUCK] ?: 0) +
                            (counts[ActorKind.BUS] ?: 0),
                    )
                    append(" vehículos")
                }
            },
            color = Mute,
            fontSize = 13.sp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0A0A0A)),
        ) {
            RoadSceneCanvas(
                actors = surround.actors,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Card)
                .padding(14.dp),
        ) {
            Text("Euphoria - Single Version", color = Mist, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text("Loreen", color = Mute, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
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
private fun RoadSceneCanvas(
    actors: List<SurroundActor>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRect(Road, topLeft = Offset(w * 0.18f, 0f), size = Size(w * 0.64f, h))
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

        // Map meters → canvas: ego at bottom-center; ahead = up; right = right
        val maxAhead = 45f
        val maxLat = 8f
        fun toCanvas(actor: SurroundActor): Offset {
            val nx = ((actor.xM / maxLat) * 0.5f + 0.5f).coerceIn(0.05f, 0.95f)
            val ny = (1f - (actor.yM / maxAhead)).coerceIn(0.05f, 0.92f)
            return Offset(nx * w, ny * h)
        }

        for (actor in actors) {
            val p = toCanvas(actor)
            when (actor.kind) {
                ActorKind.PERSON -> {
                    // small standing figure
                    drawCircle(Color(0xFFFFCC80), radius = 8f, center = p)
                    drawRoundRect(
                        Color(0xFFFFB74D),
                        topLeft = Offset(p.x - 5f, p.y - 22f),
                        size = Size(10f, 18f),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                }
                ActorKind.MOTORCYCLE, ActorKind.BICYCLE -> {
                    drawRoundRect(
                        Color(0xFF80CBC4),
                        topLeft = Offset(p.x - 14f, p.y - 10f),
                        size = Size(28f, 16f),
                        cornerRadius = CornerRadius(8f, 8f),
                    )
                    drawCircle(Color(0xFF004D40), radius = 5f, center = Offset(p.x - 10f, p.y + 6f))
                    drawCircle(Color(0xFF004D40), radius = 5f, center = Offset(p.x + 10f, p.y + 6f))
                }
                ActorKind.TRUCK, ActorKind.BUS -> {
                    val bw = w * 0.14f
                    val bh = h * 0.16f
                    drawRoundRect(
                        Color(0xFF78909C),
                        topLeft = Offset(p.x - bw / 2, p.y - bh / 2),
                        size = Size(bw, bh),
                        cornerRadius = CornerRadius(12f, 12f),
                    )
                }
                ActorKind.CAR, ActorKind.UNKNOWN -> {
                    val bw = w * 0.11f
                    val bh = h * 0.12f
                    drawRoundRect(
                        Color(0xFF9E9E9E),
                        topLeft = Offset(p.x - bw / 2, p.y - bh / 2),
                        size = Size(bw, bh),
                        cornerRadius = CornerRadius(14f, 14f),
                    )
                }
            }
        }

        // Ego car (white) — always bottom-center
        val carW = w * 0.16f
        val carH = h * 0.22f
        drawRoundRect(
            color = Color(0xFFE8E8E8),
            topLeft = Offset(w * 0.42f, h * 0.72f),
            size = Size(carW, carH),
            cornerRadius = CornerRadius(18f, 18f),
        )
    }
}
