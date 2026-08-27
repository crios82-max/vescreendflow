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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veplayer.app.data.VePrefs
import com.veplayer.app.media.MediaSource
import com.veplayer.app.media.VeMediaHub
import com.veplayer.app.radio.RadioStations
import com.veplayer.app.radio.fm.FmController
import com.veplayer.app.radio.fm.FmFreq
import com.veplayer.app.radio.fm.FmPresets
import com.veplayer.app.ui.theme.Amber
import com.veplayer.app.ui.theme.Mist
import com.veplayer.app.ui.theme.Mute
import com.veplayer.app.ui.theme.Panel
import com.veplayer.app.ui.theme.Teal

@Composable
fun RadioScreen() {
    val context = LocalContext.current
    val prefs = remember { VePrefs(context) }
    var mode by remember { mutableStateOf(prefs.radioMode) }
    val now by VeMediaHub.nowPlaying.collectAsState()
    val fm by FmController.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Radio", style = MaterialTheme.typography.headlineMedium, color = Mist, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mode == "fm") {
                Button(onClick = { mode = "fm"; prefs.radioMode = "fm" }) { Text("FM") }
                OutlinedButton(onClick = { mode = "stream"; prefs.radioMode = "stream" }) { Text("IP Stream") }
            } else {
                OutlinedButton(onClick = { mode = "fm"; prefs.radioMode = "fm" }) { Text("FM") }
                Button(onClick = { mode = "stream"; prefs.radioMode = "stream" }) { Text("IP Stream") }
            }
        }
        Text(now.status.ifBlank { "Audio idle" }, color = Teal)

        if (mode == "fm") {
            FmPanel(prefs = prefs, nowSource = now.source, fmPowered = fm.powered)
        } else {
            StreamPanel(nowSource = now.source, playing = now.source == MediaSource.RADIO && now.playing)
        }
    }
}

@Composable
private fun FmPanel(
    prefs: VePrefs,
    nowSource: MediaSource,
    fmPowered: Boolean,
) {
    val fm by FmController.state.collectAsState()
    val active = nowSource == MediaSource.FM

    Text("FM hardware · HAL RadioManager o sim (presets Caracas).", color = Mute)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                FmFreq.formatMhz(if (fm.powered) fm.freqKhz else prefs.fmLastFreqKhz),
                color = Mist,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
            )
            Text(
                buildString {
                    append(fm.status.ifBlank { "FM idle" })
                    if (fm.rdsRt.isNotBlank()) append(" · ${fm.rdsRt}")
                },
                color = Mute,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { VeMediaHub.fmStep(false) }) { Text("−") }
                OutlinedButton(onClick = { VeMediaHub.fmSeek(false) }) { Text("Seek ⟨") }
                Button(
                    onClick = {
                        if (active && fmPowered) VeMediaHub.pauseFm()
                        else VeMediaHub.playFm()
                    },
                ) {
                    Text(if (active && fmPowered) "Off" else "On")
                }
                OutlinedButton(onClick = { VeMediaHub.fmSeek(true) }) { Text("Seek ⟩") }
                OutlinedButton(onClick = { VeMediaHub.fmStep(true) }) { Text("+") }
            }
        }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(FmPresets.caracas) { station ->
            val selected = active && fm.freqKhz == station.freqKhz
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Teal.copy(0.18f) else Panel)
                    .clickable { VeMediaHub.playFm(station = station) }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(station.name, color = Mist, fontWeight = FontWeight.SemiBold)
                    Text("${station.city} · ${station.genre}", color = Mute)
                }
                Text(station.freqMhzLabel, color = Amber)
            }
        }
    }
}

@Composable
private fun StreamPanel(
    nowSource: MediaSource,
    playing: Boolean,
) {
    val now by VeMediaHub.nowPlaying.collectAsState()
    val radioActive = nowSource == MediaSource.RADIO

    Text("Streaming IP · sesión VeMediaHub (dock + DriveViz).", color = Mute)
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
                enabled = radioActive || nowSource == MediaSource.NONE,
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
