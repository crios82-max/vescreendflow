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
import com.veplayer.app.data.VePrefs
import com.veplayer.app.radio.RadioStation
import com.veplayer.app.radio.RadioStations
import com.veplayer.app.radio.fm.FmController
import com.veplayer.app.radio.fm.FmFreq
import com.veplayer.app.radio.fm.FmPresets
import com.veplayer.app.radio.fm.FmStation
import com.veplayer.app.spotify.SpotifyRemoteController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * App-wide media hub: Now Playing for DriveViz + dock,
 * ExoPlayer (IP radio), FM tuner, Spotify App Remote, audio focus.
 */
object VeMediaHub {
    private const val TAG = "VeMediaHub"

    private var app: Context? = null
    private var audio: VeAudioFocus? = null
    private var radioPlayer: ExoPlayer? = null
    private var spotify: SpotifyRemoteController? = null
    private var audioManager: AudioManager? = null
    private var prefs: VePrefs? = null

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
        prefs = VePrefs(ctx)
        audio = VeAudioFocus(ctx)
        audioManager = ctx.getSystemService(AudioManager::class.java)
        spotify = SpotifyRemoteController(ctx)
        FmController.init(ctx)
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
        FmController.powerOff()
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
        prefs?.radioMode = "stream"
        _now.value =
            NowPlaying(
                source = MediaSource.RADIO,
                title = station.name,
                artist = station.city,
                subtitle = station.genre,
                playing = true,
                progress = -1f,
                stationId = station.id,
                status = "IP Radio · ${station.name}",
            )
    }

    fun playFm(
        freqKhz: Int? = null,
        station: FmStation? = null,
    ) {
        val p = prefs ?: return
        val focus = audio ?: return
        pauseSpotifyQuiet()
        radioPlayer?.pause()
        val okFocus =
            focus.request(
                onLostFocus = {
                    FmController.powerOff()
                    _now.update { it.copy(playing = false, status = "Audio focus perdido") }
                },
            )
        if (!okFocus) {
            _now.update { it.copy(status = "Sin audio focus") }
            return
        }
        if (!FmController.ensureOpen(p)) {
            _now.update { it.copy(status = "FM no disponible") }
            return
        }
        val tuned =
            when {
                station != null -> FmController.tunePreset(station, p)
                freqKhz != null -> FmController.tune(freqKhz, p)
                else -> FmController.tune(p.fmLastFreqKhz, p)
            }
        if (!tuned) {
            _now.update { it.copy(status = "FM tune fail") }
            return
        }
        p.radioMode = "fm"
        publishFmNow(playing = true)
    }

    fun pauseFm() {
        FmController.powerOff()
        audio?.abandon()
        if (_now.value.source == MediaSource.FM) {
            _now.update { it.copy(playing = false, status = "FM off") }
        }
    }

    fun fmSeek(up: Boolean) {
        val p = prefs ?: return
        if (_now.value.source != MediaSource.FM) {
            playFm()
        }
        FmController.seek(up, p)
        publishFmNow(playing = true)
    }

    fun fmStep(up: Boolean) {
        val p = prefs ?: return
        if (_now.value.source != MediaSource.FM) playFm()
        FmController.step(up, p)
        publishFmNow(playing = true)
    }

    private fun publishFmNow(playing: Boolean) {
        val st = FmController.state.value
        val preset = FmPresets.nearest(st.freqKhz)
        val close =
            preset != null &&
                kotlin.math.abs(preset.freqKhz - st.freqKhz) <= 200
        _now.value =
            NowPlaying(
                source = MediaSource.FM,
                title =
                    when {
                        st.rdsPs.isNotBlank() -> st.rdsPs
                        close -> preset!!.name
                        else -> FmFreq.formatMhz(st.freqKhz)
                    },
                artist = if (close) preset!!.city else st.backend.uppercase(),
                subtitle =
                    buildString {
                        append(FmFreq.formatMhz(st.freqKhz))
                        if (st.signalPct > 0) append(" · sig ${st.signalPct}%")
                        if (st.stereo) append(" · ST")
                        if (close) append(" · ${preset!!.genre}")
                    },
                playing = playing && st.powered,
                progress = (st.signalPct / 100f).coerceIn(0f, 1f),
                stationId = if (close) preset!!.id else null,
                fmFreqKhz = st.freqKhz,
                status = st.status,
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
        pauseFm()
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
        pauseFm()
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

    /** External phone / CarPlay / Android Auto now-playing mirror. */
    fun publishPhone(
        title: String,
        artist: String,
        playing: Boolean,
        status: String,
    ) {
        val cur = _now.value.source
        if (cur != MediaSource.NONE && cur != MediaSource.PHONE) return
        _now.value =
            NowPlaying(
                source = MediaSource.PHONE,
                title = title,
                artist = artist,
                subtitle = "Phone Link",
                playing = playing,
                progress = -1f,
                status = status,
            )
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
            MediaSource.FM -> {
                if (_now.value.playing) pauseFm()
                else playFm(_now.value.fmFreqKhz)
            }
            MediaSource.SPOTIFY -> {
                if (_now.value.playing) pauseSpotify() else resumeSpotify()
            }
            MediaSource.PHONE -> {
                _now.update { it.copy(playing = !it.playing, status = "Phone Link (AVRCP HU)") }
            }
            MediaSource.NONE -> {
                val mode = prefs?.radioMode ?: "stream"
                if (mode == "fm") playFm() else playRadio(RadioStations.all.first())
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
            MediaSource.FM -> fmSeek(up = true)
            MediaSource.SPOTIFY -> spotify?.skipNext { msg -> _now.update { it.copy(status = msg) } }
            MediaSource.PHONE -> _now.update { it.copy(status = "Skip · phone") }
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
            MediaSource.FM -> fmSeek(up = false)
            MediaSource.SPOTIFY -> spotify?.skipPrevious { msg -> _now.update { it.copy(status = msg) } }
            MediaSource.PHONE -> _now.update { it.copy(status = "Prev · phone") }
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
        FmController.powerOff()
        audio?.abandon()
        spotify?.disconnect()
        spotify = null
        app = null
    }
}
