package com.veplayer.app.ui.screens

import android.net.Uri
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.veplayer.app.audio.VeAudioFocus
import com.veplayer.app.radio.RadioStation
import com.veplayer.app.radio.RadioStations
import com.veplayer.app.ui.theme.Amber
import com.veplayer.app.ui.theme.Mist
import com.veplayer.app.ui.theme.Mute
import com.veplayer.app.ui.theme.Panel
import com.veplayer.app.ui.theme.Teal

@Composable
fun RadioScreen() {
    val context = LocalContext.current
    var current by remember { mutableStateOf<RadioStation?>(null) }
    var playing by remember { mutableStateOf(false) }
    var focusNote by remember { mutableStateOf("Audio focus: idle") }
    val player = remember { ExoPlayer.Builder(context).build() }
    val audio = remember { VeAudioFocus(context) }

    DisposableEffect(Unit) {
        onDispose {
            audio.abandon()
            player.release()
        }
    }

    fun play(station: RadioStation) {
        val ok =
            audio.request(
                onLostFocus = {
                    player.pause()
                    playing = false
                    focusNote = "Audio focus perdido (llamada/otra app) — radio en pausa"
                },
            )
        if (!ok) {
            focusNote = "Sin audio focus — no se puede reproducir"
            return
        }
        focusNote = "Audio focus OK"
        current = station
        player.setMediaItem(MediaItem.fromUri(Uri.parse(station.streamUrl)))
        player.prepare()
        player.play()
        playing = true
    }

    fun toggle() {
        if (playing) {
            player.pause()
            playing = false
            audio.abandon()
            focusNote = "Audio focus liberado"
        } else {
            current?.let { play(it) }
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Radio", style = MaterialTheme.typography.headlineMedium, color = Mist, fontWeight = FontWeight.Bold)
        Text(
            "Streaming IP + audio focus (cede ante llamadas / Spotify).",
            color = Mute,
        )
        Text(focusNote, color = Teal)
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
                    Text(current?.name ?: "Sin emisora", color = Teal, fontWeight = FontWeight.Bold)
                    Text(current?.city ?: "Elige una estación", color = Mute)
                }
                Button(onClick = { if (current != null) toggle() }, enabled = current != null) {
                    Text(if (playing) "Pausar" else "Play")
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(RadioStations.all) { station ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (current?.id == station.id) Teal.copy(0.18f) else Panel)
                        .clickable { play(station) }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(station.name, color = Mist, fontWeight = FontWeight.SemiBold)
                        Text("${station.city} · ${station.genre}", color = Mute)
                    }
                    Text(if (current?.id == station.id && playing) "▶" else "○", color = Amber)
                }
            }
        }
    }
}
