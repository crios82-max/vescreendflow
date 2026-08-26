package com.veplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veplayer.app.ui.VeDest
import com.veplayer.app.ui.theme.Amber
import com.veplayer.app.ui.theme.Mist
import com.veplayer.app.ui.theme.Mute
import com.veplayer.app.ui.theme.Panel
import com.veplayer.app.ui.theme.Teal

@Composable
fun HomeScreen(onOpen: (VeDest) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Centro de control", style = MaterialTheme.typography.headlineMedium, color = Mist, fontWeight = FontWeight.Bold)
        Text(
            "Cámaras · Radio · YouTube · Tienda (Spotify) · Pantalla · Mapa SenseFlow",
            color = Mute,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            HomeCard("Cámaras", "Frontal + trasera", Teal, Modifier.weight(1f)) { onOpen(VeDest.Cameras) }
            HomeCard("Radio", "Emisoras en vivo", Amber, Modifier.weight(1f)) { onOpen(VeDest.Radio) }
            HomeCard("YouTube", "Video a bordo", Teal, Modifier.weight(1f)) { onOpen(VeDest.YouTube) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            HomeCard("Tienda", "Spotify y enlaces", Amber, Modifier.weight(1f)) { onOpen(VeDest.Store) }
            HomeCard("Pantalla", "vescreenflow", Teal, Modifier.weight(1f)) { onOpen(VeDest.Player) }
            HomeCard("Mapa", "Tráfico + personas", Amber, Modifier.weight(1f)) { onOpen(VeDest.Map) }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Modo vehículo", color = Teal, fontWeight = FontWeight.Bold)
                Text(
                    "Launcher kiosk · boot auto · SenseFlow en background. " +
                        "FM hardware opcional; radio por streaming si no hay sintonizador.",
                    color = Mute,
                )
            }
        }
    }
}

@Composable
private fun HomeCard(
    title: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(140.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Panel)
            .clickable(onClick = onClick)
            .padding(18.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column {
            Text(title, color = accent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Mute)
        }
    }
}
