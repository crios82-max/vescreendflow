package com.veplayer.app.spotify

import android.content.Context
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.veplayer.app.BuildConfig

class SpotifyRemoteController(
    private val context: Context,
) {
    private var remote: SpotifyAppRemote? = null

    val isConnected: Boolean get() = remote?.isConnected == true

    fun connect(
        onStatus: (String) -> Unit,
        onConnected: () -> Unit = {},
    ) {
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        if (clientId.isBlank() || clientId == "YOUR_SPOTIFY_CLIENT_ID") {
            onStatus(
                "Configura SPOTIFY_CLIENT_ID (Spotify Developer Dashboard) en build.gradle.kts / local.properties",
            )
            return
        }
        val params =
            ConnectionParams.Builder(clientId)
                .setRedirectUri(BuildConfig.SPOTIFY_REDIRECT_URI)
                .showAuthView(true)
                .build()

        onStatus("Conectando App Remote…")
        SpotifyAppRemote.connect(
            context,
            params,
            object : Connector.ConnectionListener {
                override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                    remote = spotifyAppRemote
                    onStatus("Spotify App Remote enlazado")
                    onConnected()
                }

                override fun onFailure(error: Throwable) {
                    Log.w(TAG, "Spotify connect failed", error)
                    onStatus(
                        "No se pudo enlazar: ${error.message}. " +
                            "Instala Spotify, inicia sesión y verifica Client ID + redirect URI.",
                    )
                }
            },
        )
    }

    fun playUri(uri: String, onStatus: (String) -> Unit) {
        val r = remote
        if (r == null || !r.isConnected) {
            onStatus("Primero enlaza Spotify App Remote")
            return
        }
        r.playerApi.play(uri)
            .setResultCallback { onStatus("Reproduciendo $uri") }
            .setErrorCallback { onStatus("Play error: ${it.message}") }
    }

    fun resume(onStatus: (String) -> Unit) {
        remote?.playerApi?.resume()
            ?.setResultCallback { onStatus("▶ Play") }
            ?: onStatus("Sin conexión App Remote")
    }

    fun pause(onStatus: (String) -> Unit) {
        remote?.playerApi?.pause()
            ?.setResultCallback { onStatus("⏸ Pause") }
            ?: onStatus("Sin conexión App Remote")
    }

    fun disconnect() {
        remote?.let { SpotifyAppRemote.disconnect(it) }
        remote = null
    }

    companion object {
        private const val TAG = "SpotifyRemote"
    }
}
