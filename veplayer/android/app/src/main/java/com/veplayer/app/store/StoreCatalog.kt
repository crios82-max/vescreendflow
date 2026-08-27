package com.veplayer.app.store

data class StoreApp(
    val id: String,
    val name: String,
    val packageName: String,
    val playStoreUrl: String,
    val blurb: String,
    val howToLink: String,
    val deepLink: String? = null,
)

object StoreCatalog {
    val apps =
        listOf(
            StoreApp(
                id = "spotify",
                name = "Spotify",
                packageName = "com.spotify.music",
                playStoreUrl = "https://play.google.com/store/apps/details?id=com.spotify.music",
                blurb = "Reproduce música en el vehículo. Usa Spotify Connect desde el teléfono.",
                howToLink =
                    "1) Instala Spotify en el head-unit. 2) Inicia sesión. " +
                        "3) En el móvil: Dispositivos disponibles → elige este VePlayer / Spotify Connect.",
                deepLink = "spotify://",
            ),
            StoreApp(
                id = "youtube",
                name = "YouTube",
                packageName = "com.google.android.youtube",
                playStoreUrl = "https://play.google.com/store/apps/details?id=com.google.android.youtube",
                blurb = "App nativa de YouTube (alternativa a la pestaña embebida).",
                howToLink = "Instala y abre desde aquí. Ideal si el WebView limita DRM.",
            ),
            StoreApp(
                id = "spotify-connect-guide",
                name = "Guía Spotify Connect",
                packageName = "com.spotify.music",
                playStoreUrl = "https://support.spotify.com/article/spotify-connect/",
                blurb = "Documentación oficial para enlazar teléfono ↔ reproductor del auto.",
                howToLink = "Abre la guía y sigue los pasos de Spotify Connect en la misma red Wi‑Fi/hotspot.",
                deepLink = "https://support.spotify.com/article/spotify-connect/",
            ),
        )
}
