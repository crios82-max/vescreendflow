package com.veplayer.app.media

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.spotify.protocol.types.PlayerState
import com.veplayer.app.audio.VeAudioFocus
import com.veplayer.app.radio.RadioStation
import com.veplayer.app.radio.RadioStations
import com.veplayer.app.spotify.SpotifyRemoteController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * App-wide media hub: one Now Playing surface for DriveViz + dock,
 * shared ExoPlayer (radio), Spotify App Remote, and audio focus.
 */
object VeMediaHub {
    private const val TAG = "VeMediaHub"

    private var app: Context? = null
    private var audio: VeAudioFocus? = null
    private var radioPlayer: ExoPlayer? = null
    private var spotify: SpotifyRemoteController? = null
    private var audioManager: AudioManager? = null

    private val _now = MutableStateFlow(NowPlaying())
    val nowPlaying: StateFlow<NowPlaying> = _now.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private var preMuteVolume = 8

    @Synchronized
    fun init(context: Context) {
        if (app != null) return
        val ctx = context.applicationContext
        app = ctx
        audio = VeAudioFocus(ctx)
        audioManager = ctx.getSystemService(AudioManager::class.java)
        spotify = SpotifyRemoteController(ctx)
        radioPlayer =
            ExoPlayer.Builder(ctx).build().also { player ->
                player.addListener(
                    object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (_now.value.source == MediaSource.RADIO) {
                                _now.update { it.copy(playing = isPlaying) }
                            }
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (_now.value.source != MediaSource.RADIO) return
                            if (playbackState == Player.STATE_BUFFERING) {
                                _now.update { it.copy(status = "Buffering…") }
                            } else if (playbackState == Player.STATE_READY) {
                                _now.update { it.copy(status = "Radio live") }
                            }
                        }
                    },
                )
            }
        Log.i(TAG, "media hub ready")
    }

    fun spotifyController(): SpotifyRemoteController? = spotify

    fun playRadio(station: RadioStation) {
        val player = radioPlayer ?: return
        val focus = audio ?: return
        pauseSpotifyQuiet()
        val ok =
            focus.request(
                onLostFocus = {
                    player.pause()
                    _now.update { it.copy(playing = false, status = "Audio focus perdido") }
                },
            )
        if (!ok) {
            _now.update { it.copy(status = "Sin audio focus") }
            return
        }
        player.setMediaItem(MediaItem.fromUri(Uri.parse(station.streamUrl)))
        player.prepare()
        player.play()
        _now.value =
            NowPlaying(
                source = MediaSource.RADIO,
                title = station.name,
                artist = station.city,
                subtitle = station.genre,
                playing = true,
                progress = -1f,
                stationId = station.id,
                status = "Radio · ${station.name}",
            )
    }

    fun pauseRadio() {
        radioPlayer?.pause()
        audio?.abandon()
        if (_now.value.source == MediaSource.RADIO) {
            _now.update { it.copy(playing = false, status = "Radio pausada") }
        }
    }

    fun connectSpotify(onStatus: (String) -> Unit) {
        val s = spotify ?: return
        s.connect(
            onStatus = { msg ->
                onStatus(msg)
                _now.update { it.copy(status = msg) }
            },
            onConnected = {
                s.subscribePlayerState { state -> applySpotifyState(state) }
            },
        )
    }

    fun playSpotifyUri(uri: String, onStatus: (String) -> Unit = {}) {
        pauseRadio()
        val s = spotify ?: return
        s.playUri(uri) { msg ->
            onStatus(msg)
            _now.update {
                it.copy(
                    source = MediaSource.SPOTIFY,
                    spotifyUri = uri,
                    status = msg,
                    playing = true,
                )
            }
        }
    }

    fun resumeSpotify(onStatus: (String) -> Unit = {}) {
        pauseRadio()
        spotify?.resume { msg ->
            onStatus(msg)
            _now.update { it.copy(source = MediaSource.SPOTIFY, playing = true, status = msg) }
        }
    }

    fun pauseSpotify(onStatus: (String) -> Unit = {}) {
        spotify?.pause { msg ->
            onStatus(msg)
            if (_now.value.source == MediaSource.SPOTIFY) {
                _now.update { it.copy(playing = false, status = msg) }
            }
        }
    }

    private fun pauseSpotifyQuiet() {
        runCatching { spotify?.pause { } }
    }

    fun togglePlayPause() {
        when (_now.value.source) {
            MediaSource.RADIO -> {
                if (_now.value.playing) pauseRadio()
                else {
                    val id = _now.value.stationId
                    val st = RadioStations.all.firstOrNull { it.id == id } ?: RadioStations.all.first()
                    playRadio(st)
                }
            }
            MediaSource.SPOTIFY -> {
                if (_now.value.playing) pauseSpotify() else resumeSpotify()
            }
            MediaSource.NONE -> {
                playRadio(RadioStations.all.first())
            }
        }
    }

    fun skipNext() {
        when (_now.value.source) {
            MediaSource.RADIO -> {
                val list = RadioStations.all
                val idx = list.indexOfFirst { it.id == _now.value.stationId }.coerceAtLeast(0)
                playRadio(list[(idx + 1) % list.size])
            }
            MediaSource.SPOTIFY -> spotify?.skipNext { msg -> _now.update { it.copy(status = msg) } }
            MediaSource.NONE -> playRadio(RadioStations.all.first())
        }
    }

    fun skipPrevious() {
        when (_now.value.source) {
            MediaSource.RADIO -> {
                val list = RadioStations.all
                val idx = list.indexOfFirst { it.id == _now.value.stationId }.coerceAtLeast(0)
                playRadio(list[(idx - 1 + list.size) % list.size])
            }
            MediaSource.SPOTIFY -> spotify?.skipPrevious { msg -> _now.update { it.copy(status = msg) } }
            MediaSource.NONE -> playRadio(RadioStations.all.last())
        }
    }

    fun toggleMute() {
        val am = audioManager ?: return
        if (_muted.value) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, preMuteVolume.coerceAtLeast(1), 0)
            _muted.value = false
            _now.update { it.copy(status = "Unmute") }
        } else {
            preMuteVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            _muted.value = true
            _now.update { it.copy(status = "Mute") }
        }
    }

    fun adjustVolume(raise: Boolean) {
        val am = audioManager ?: return
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI,
        )
        if (_muted.value && raise) _muted.value = false
    }

    private fun applySpotifyState(state: PlayerState) {
        val track = state.track
        _now.value =
            NowPlaying(
                source = MediaSource.SPOTIFY,
                title = track?.name ?: "Spotify",
                artist = track?.artist?.name ?: "Spotify",
                subtitle = track?.album?.name.orEmpty(),
                playing = !state.isPaused,
                progress =
                    if (track != null && track.duration > 0) {
                        (state.playbackPosition.toFloat() / track.duration.toFloat()).coerceIn(0f, 1f)
                    } else {
                        -1f
                    },
                spotifyUri = track?.uri,
                status = if (state.isPaused) "Spotify pausado" else "Spotify",
            )
    }

    fun release() {
        radioPlayer?.release()
        radioPlayer = null
        audio?.abandon()
        spotify?.disconnect()
        spotify = null
        app = null
    }
}
