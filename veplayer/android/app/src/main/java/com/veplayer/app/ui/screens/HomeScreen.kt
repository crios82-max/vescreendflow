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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veplayer.app.kiosk.KioskController
import com.veplayer.app.brand.BrandBus
import com.veplayer.app.ui.brand.BrandLogo
import com.veplayer.app.ui.VeDest
import com.veplayer.app.ui.theme.Amber
import com.veplayer.app.ui.theme.Mist
import com.veplayer.app.ui.theme.Mute
import com.veplayer.app.ui.theme.Panel
import com.veplayer.app.ui.theme.Teal

@Composable
fun HomeScreen(onOpen: (VeDest) -> Unit) {
    val context = LocalContext.current
    val kiosk = KioskController.statusLabel(context)
  LaunchedEffect(Unit) { BrandBus.refresh(context) }
    val brand by BrandBus.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (brand.displayName.isNotBlank()) brand.displayName else "Centro de control",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(brand.accentArgb),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Cámaras · Radio · YouTube · Tienda (Spotify) · Pantalla · Mapa SenseFlow",
                    color = Mute,
                )
            }
            if (brand.hasLogo) {
                BrandLogo(height = 48.dp)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Panel)
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Kiosk", color = Teal, fontWeight = FontWeight.Bold)
                Text(kiosk, color = Mist)
                Text(
                    "Device Owner: veplayer/scripts/enable-device-owner.sh",
                    color = Mute,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            HomeCard("Cámaras", "Frontal + trasera/USB", Teal, Modifier.weight(1f)) { onOpen(VeDest.Cameras) }
            HomeCard("Radio", "FM + stream IP", Amber, Modifier.weight(1f)) { onOpen(VeDest.Radio) }
            HomeCard("YouTube", "Video a bordo", Teal, Modifier.weight(1f)) { onOpen(VeDest.YouTube) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            HomeCard("Tienda", "Spotify App Remote", Amber, Modifier.weight(1f)) { onOpen(VeDest.Store) }
            HomeCard("Pantalla", "vescreenflow", Teal, Modifier.weight(1f)) { onOpen(VeDest.Player) }
            HomeCard("Mapa", "Tráfico + personas", Amber, Modifier.weight(1f)) { onOpen(VeDest.Map) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            HomeCard("Ajustes", "PIN · flota · OTA · mock", Teal, Modifier.weight(1f)) { onOpen(VeDest.Settings) }
            Box(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.weight(1f))
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
                    "Launcher kiosk · boot auto · SenseFlow · ConcurrentCamera dual · Spotify App Remote.",
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
