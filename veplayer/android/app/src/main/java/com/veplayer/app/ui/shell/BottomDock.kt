package com.veplayer.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veplayer.app.media.VeMediaHub
import com.veplayer.app.ui.VeDest
import com.veplayer.app.ui.theme.Accent
import com.veplayer.app.ui.theme.Mist
import com.veplayer.app.ui.theme.Mute
import com.veplayer.app.ui.theme.Teal
import com.veplayer.app.vehicle.VehicleState

private data class DockItem(
    val dest: VeDest,
    val icon: ImageVector,
    val tint: Color,
)

@Composable
fun BottomDock(
    current: VeDest,
    onSelect: (VeDest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val media by VeMediaHub.nowPlaying.collectAsState()
    val muted by VeMediaHub.muted.collectAsState()
    val vehicle by VehicleState.state.collectAsState()
    val hvac by HvacClimateMonitor.state.collectAsState()
    val cabin =
        if (hvac.showPanel) HvacClimate.dockLabel(hvac)
        else vehicle.hvacCabinC?.let { "%.1f°".format(it) } ?: "—"
    val cabinColor =
        if (hvac.showPanel) Color(HvacClimate.accentArgb(hvac.band)) else Mist

    val items =
        listOf(
            DockItem(VeDest.Home, Icons.Default.DirectionsCar, Mist),
            DockItem(VeDest.Map, Icons.Default.Map, Teal),
            DockItem(VeDest.Radio, Icons.Default.MusicNote, Mist),
            DockItem(VeDest.Store, Icons.Default.Storefront, Accent),
            DockItem(VeDest.Cameras, Icons.Default.Videocam, Mute),
            DockItem(VeDest.YouTube, Icons.Default.PlayCircle, Color(0xFFFF4444)),
            DockItem(VeDest.Player, Icons.Default.Tv, Mist),
            DockItem(VeDest.Settings, Icons.Default.Settings, Mute),
        )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color(0xFF0A0A0A))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Mist, modifier = Modifier.size(22.dp))
            Text(cabin, color = cabinColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Icon(
                if (media.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Mist,
                modifier = Modifier
                    .size(26.dp)
                    .clickable { VeMediaHub.togglePlayPause() },
            )
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "Next",
                tint = Mute,
                modifier = Modifier
                    .size(22.dp)
                    .clickable { VeMediaHub.skipNext() },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEach { item ->
                val selected = item.dest == current
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) Color(0xFF222222) else Color.Transparent)
                        .clickable { onSelect(item.dest) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.dest.label,
                        tint = if (selected) Mist else item.tint.copy(alpha = 0.85f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Apps, contentDescription = null, tint = Mute, modifier = Modifier.size(20.dp))
            Icon(
                if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = "Mute",
                tint = Mist,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { VeMediaHub.toggleMute() },
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (media.playing) Accent else Mute),
            )
        }
    }
}
