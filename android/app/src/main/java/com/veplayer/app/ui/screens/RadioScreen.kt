package com.veplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veplayer.app.media.MediaSource
import com.veplayer.app.media.VeMediaHub
import com.veplayer.app.radio.RadioStations
import com.veplayer.app.ui.theme.Amber
import com.veplayer.app.ui.theme.Mist
import com.veplayer.app.ui.theme.Mute
import com.veplayer.app.ui.theme.Panel
import com.veplayer.app.ui.theme.Teal

@Composable
fun RadioScreen() {
    val now by VeMediaHub.nowPlaying.collectAsState()
    val radioActive = now.source == MediaSource.RADIO
    val playing = radioActive && now.playing

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Radio", style = MaterialTheme.typography.headlineMedium, color = Mist, fontWeight = FontWeight.Bold)
        Text(
            "Streaming IP · sesión unificada VeMediaHub (dock + DriveViz).",
            color = Mute,
        )
        Text(now.status.ifBlank { "Audio idle" }, color = Teal)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        if (radioActive) now.title else "Sin emisora",
                        color = Teal,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (radioActive) "${now.artist} · ${now.subtitle}" else "Elige una estación",
                        color = Mute,
                    )
                }
                Button(
                    onClick = { VeMediaHub.togglePlayPause() },
                    enabled = radioActive || now.source == MediaSource.NONE,
                ) {
                    Text(if (playing) "Pausar" else "Play")
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(RadioStations.all) { station ->
                val selected = radioActive && now.stationId == station.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Teal.copy(0.18f) else Panel)
                        .clickable { VeMediaHub.playRadio(station) }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(station.name, color = Mist, fontWeight = FontWeight.SemiBold)
                        Text("${station.city} · ${station.genre}", color = Mute)
                    }
                    Text(if (selected && playing) "▶" else "○", color = Amber)
                }
            }
        }
    }
}
