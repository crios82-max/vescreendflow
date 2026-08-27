package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watches vehicle speed vs limit (base prefs or active geofence zone).
 */
object SpeedHudMonitor {
    private val _state = MutableStateFlow(SpeedHud.evaluate(0f, 50))
    val state: StateFlow<SpeedHud.State> = _state.asStateFlow()

    private var overSinceMs = 0L
    private var lastSpokenMs = 0L
    private var lastZoneAnnounceId = -1
    private const val HOLD_MS = 2_500L
    private const val COOLDOWN_MS = 45_000L

    /** Effective limit: zone max when enabled, else prefs. */
    fun effectiveLimitKmh(prefs: VePrefs): Int {
        val zone = SpeedZoneBus.zone.value
        if (prefs.geofenceSpeedEnabled && zone != null && zone.maxKmh in 10..160) {
            return zone.maxKmh
        }
        return prefs.speedLimitKmh
    }

    fun tick(prefs: VePrefs, speedKmh: Float) {
        val limit = effectiveLimitKmh(prefs)
        val zone = SpeedZoneBus.zone.value
        if (zone != null && zone.id != lastZoneAnnounceId && prefs.geofenceSpeedEnabled) {
            lastZoneAnnounceId = zone.id
            NavTts.speakNow("Zona ${zone.name}. Límite ${zone.maxKmh} kilómetros por hora.")
        }
        if (zone == null) lastZoneAnnounceId = -1

        if (!prefs.speedHudEnabled) {
            _state.value = SpeedHud.evaluate(speedKmh, limit, prefs.speedWarnMarginKmh)
            return
        }
        val st =
            SpeedHud.evaluate(
                speedKmh = speedKmh,
                limitKmh = limit,
                warnMarginKmh = prefs.speedWarnMarginKmh,
            )
        _state.value = st
        val now = System.currentTimeMillis()
        if (st.showWarn) {
            if (overSinceMs == 0L) overSinceMs = now
            val held = now - overSinceMs >= HOLD_MS
            val cooled = now - lastSpokenMs >= COOLDOWN_MS
            if (held && cooled && prefs.speedTtsWarn) {
                lastSpokenMs = now
                val phrase =
                    if (zone != null) {
                        "Exceso en zona ${zone.name}. Límite ${st.limitKmh}, vas a ${st.speedKmh.toInt()}."
                    } else {
                        SpeedHud.voicePhrase(st)
                    }
                NavTts.speakNow(phrase)
                FleetInbox.push(
                    prefs = prefs,
                    kind = if (zone != null) "geofence_speed" else "overspeed",
                    text = phrase,
                    severity = "warn",
                    id = "overspeed:${st.limitKmh}:${zone?.id ?: 0}:${now / 60_000}",
                    speak = false,
                )
            }
        } else {
            overSinceMs = 0L
        }
    }
}
