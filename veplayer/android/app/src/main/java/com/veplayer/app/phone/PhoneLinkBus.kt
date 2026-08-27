package com.veplayer.app.phone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phone projection / BT link state (Android Auto · CarPlay · A2DP).
 * Full AA/CarPlay host stacks require OEM/MFi — this bus tracks link + media + demo sim.
 */
object PhoneLinkBus {
    enum class Protocol {
        NONE,
        BT_MEDIA,
        ANDROID_AUTO,
        CARPLAY,
    }

    data class State(
        val enabled: Boolean = true,
        val connected: Boolean = false,
        val protocol: Protocol = Protocol.NONE,
        val deviceName: String = "",
        val mediaTitle: String = "",
        val mediaArtist: String = "",
        val playing: Boolean = false,
        /** Host stack available on this ROM (OEM). */
        val aaHostAvailable: Boolean = false,
        val carplayHostAvailable: Boolean = false,
        val statusText: String = "Sin teléfono",
        val simulated: Boolean = false,
    ) {
        fun toJsonMap(): Map<String, Any?> =
            mapOf(
                "connected" to connected,
                "protocol" to protocol.name.lowercase(),
                "device_name" to deviceName.ifBlank { null },
                "media_title" to mediaTitle.ifBlank { null },
                "media_artist" to mediaArtist.ifBlank { null },
                "playing" to playing,
                "aa_host" to aaHostAvailable,
                "carplay_host" to carplayHostAvailable,
                "simulated" to simulated,
                "status" to statusText,
            )
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun publish(s: State) {
        _state.value = s
    }
}
