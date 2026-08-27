package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Parking distance HUD: evaluate zones, TTS + inbox on warn/crit while reverse.
 */
object ParkingDistanceMonitor {
    private val _state = MutableStateFlow(ParkingDistance.State())
    val state: StateFlow<ParkingDistance.State> = _state.asStateFlow()

    private var warnSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastBand = ""
    private const val HOLD_MS = 800L
    private const val COOLDOWN_MS = 8_000L

    fun tick(
        prefs: VePrefs,
        reverse: Boolean,
        zones: ParkingDistance.Zones = ParkingDistanceBus.zones.value,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (prefs.parkingHudEnabled && reverse && prefs.parkingSimEnabled) {
            ParkingDistanceBus.tickSim(reverse, true, VehicleState.state.value.steeringAngleDeg)
        } else if (!reverse) {
            ParkingDistanceBus.clear()
        }

        val st =
            ParkingDistance.evaluate(
                zones = if (reverse) ParkingDistanceBus.zones.value else ParkingDistance.Zones(),
                reverse = reverse && prefs.parkingHudEnabled,
                warnM = prefs.parkingWarnM,
                critM = prefs.parkingCritM,
            )
        _state.value = st

        // Keep VehicleState.uss* in sync for heartbeat / UI
        if (st.active || reverse) {
            VehicleState.applySignals(VehicleState.state.value)
        }

        if (!prefs.parkingHudEnabled || !st.active || !st.showWarn) {
            if (!st.showWarn) {
                warnSinceMs = 0L
                lastBand = ""
            }
            return
        }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val held = nowMs - warnSinceMs >= HOLD_MS
        val cooled = nowMs - lastSpokenMs >= COOLDOWN_MS
        val bandChanged = st.band != lastBand
        if (held && (cooled || bandChanged) && prefs.parkingTts) {
            lastSpokenMs = nowMs
            lastBand = st.band
            val phrase = ParkingDistance.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "crit") "parking_crit" else "parking_near",
                text = phrase,
                severity = if (st.band == "crit") "warn" else "info",
                id = "parking:${st.band}:${nowMs / 30_000}",
                speak = false,
            )
        }
    }
}
