package com.veplayer.app.media

/** Active playback backend for the unified cockpit media controls. */
enum class MediaSource {
    NONE,
    RADIO,
    SPOTIFY,
}

data class NowPlaying(
    val source: MediaSource = MediaSource.NONE,
    val title: String = "Sin reproducción",
    val artist: String = "VePlayer",
    val subtitle: String = "",
    val playing: Boolean = false,
    /** 0f..1f when known; radio streams stay indeterminate (~-1). */
    val progress: Float = -1f,
    val stationId: String? = null,
    val spotifyUri: String? = null,
    val status: String = "",
)
