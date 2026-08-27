package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.GeoProjection
import com.veplayer.app.nav.LatLng
import com.veplayer.app.nav.NavEngine
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Distance to active nav polyline → off-route warn/alert · TTS + inbox.
 */
object RouteDeviationMonitor {
    private val _state = MutableStateFlow(RouteDeviation.State())
    val state: StateFlow<RouteDeviation.State> = _state.asStateFlow()

    private var offSinceMs = 0L
    private var lastTsMs = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val COOLDOWN_MS = 60_000L

    fun tick(
        prefs: VePrefs,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (!prefs.routeDevEnabled) {
            _state.value = RouteDeviation.State()
            offSinceMs = 0L
            lastTsMs = 0L
            return
        }

        val route = NavEngine.route.value
        val geom = route.geometry
        val hasRoute =
            prefs.navEnabled &&
                geom.size >= 2 &&
                route.source != "off" &&
                route.source != "idle" &&
                route.distanceM > 0

        val measured =
            if (hasRoute) {
                val path = geom.map { LatLng(it.first, it.second) }
                val ego = LatLng(prefs.navFromLat, prefs.navFromLng)
                GeoProjection.distanceToRouteM(path, ego).toFloat()
            } else {
                0f
            }
        val dist =
            when {
                prefs.routeDevSimM > 0f -> prefs.routeDevSimM
                else -> measured
            }
        val effectiveHasRoute = hasRoute || prefs.routeDevSimM > 0f

        val warnM = prefs.routeDevWarnM
        val offNow = effectiveHasRoute && dist >= warnM
        val offRouteSec =
            if (offNow) {
                if (offSinceMs == 0L) offSinceMs = nowMs
                ((nowMs - offSinceMs) / 1000f).coerceAtLeast(0f)
            } else {
                offSinceMs = 0L
                0f
            }
        lastTsMs = nowMs

        val st =
            RouteDeviation.evaluate(
                distanceM = dist,
                offRouteSec = offRouteSec,
                hasRoute = effectiveHasRoute,
                warnM = warnM,
                alertM = prefs.routeDevAlertM,
                holdSec = prefs.routeDevHoldSec,
            )
        _state.value = st

        if (!st.showWarn) {
            lastKey = ""
            return
        }
        val cooled = nowMs - lastSpokenMs >= COOLDOWN_MS
        val key = "${st.band}:${(st.distanceM / 10).toInt()}"
        val changed = key != lastKey
        if ((cooled || changed) && prefs.routeDevTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = RouteDeviation.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(
                prefs = prefs,
                kind = if (st.band == "alert") "route_deviate" else "route_warn",
                text = phrase,
                severity = if (st.band == "alert") "critical" else "warn",
                id = "route:${st.band}:${nowMs / 120_000}",
                speak = false,
            )
        }
    }
}
