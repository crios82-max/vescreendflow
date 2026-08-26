package com.veplayer.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import com.veplayer.app.store.StoreApp
import com.veplayer.app.store.StoreCatalog
import com.veplayer.app.spotify.SpotifyRemoteController
import com.veplayer.app.ui.theme.Mist
import com.veplayer.app.ui.theme.Mute
import com.veplayer.app.ui.theme.Panel
import com.veplayer.app.ui.theme.Teal

@Composable
fun StoreScreen() {
    val context = LocalContext.current
    val spotify = remember { SpotifyRemoteController(context.applicationContext) }
    var spotifyStatus by remember { mutableStateOf("Spotify App Remote: desconectado") }

    DisposableEffect(Unit) {
        onDispose { spotify.disconnect() }
    }

    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
        }
    }

    fun openPackageOrStore(app: StoreApp) {
        val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
        if (launch != null) {
            context.startActivity(launch)
            return
        }
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}")),
            )
        } catch (_: ActivityNotFoundException) {
            openUrl(app.playStoreUrl)
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tienda VePlayer", style = MaterialTheme.typography.headlineMedium, color = Mist, fontWeight = FontWeight.Bold)
        Text(
            "Instala apps oficiales y enlaza Spotify con App Remote SDK (sin APKs pirateados).",
            color = Mute,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Spotify App Remote", color = Teal, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(
                "Enlaza la app Spotify instalada en este head-unit. Requiere Client ID del Spotify Developer Dashboard.",
                color = Mute,
            )
            Text(spotifyStatus, color = Mist)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { spotify.connect(onStatus = { spotifyStatus = it }) }) {
                    Text("Enlazar dispositivo")
                }
                OutlinedButton(onClick = { spotify.resume { spotifyStatus = it } }) { Text("Play") }
                OutlinedButton(onClick = { spotify.pause { spotifyStatus = it } }) { Text("Pause") }
            }
            OutlinedButton(
                onClick = {
                    spotify.playUri("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M") { spotifyStatus = it }
                },
            ) {
                Text("Probar playlist Today’s Top Hits")
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(StoreCatalog.apps) { app ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Panel)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(app.name, color = Teal, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text(app.blurb, color = Mute)
                    Text(app.howToLink, color = Mist)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { openPackageOrStore(app) }) {
                            Text(if (app.id == "spotify") "Instalar / abrir Spotify" else "Abrir / instalar")
                        }
                        if (app.deepLink != null) {
                            OutlinedButton(onClick = { openUrl(app.deepLink) }) {
                                Text("Guía Connect")
                            }
                        }
                    }
                }
            }
        }
    }
}
