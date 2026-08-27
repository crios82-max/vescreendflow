package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.fleet.ShiftTracker
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Safety scorecard for open shift · accumulates harsh / overspeed / seatbelt / impact / route.
 */
object DriverScoreMonitor {
    private val _state = MutableStateFlow(DriverScore.State())
    val state: StateFlow<DriverScore.State> = _state.asStateFlow()

    private var harshBrakeN = 0
    private var harshAccelN = 0
    private var overspeedMs = 0L
    private var seatbeltN = 0
    private var impactN = 0
    private var routeDevMs = 0L
    private var lastHarshKey = ""
    private var lastSeatbeltWarn = false
    private var lastImpactWarn = false
    private var lastTsMs = 0L
    private var lastShiftId = 0L
    private var lastSpokenMs = 0L
    private var lastKey = ""
    private const val COOLDOWN_MS = 5 * 60_000L

    fun reset() {
        harshBrakeN = 0
        harshAccelN = 0
        overspeedMs = 0L
        seatbeltN = 0
        impactN = 0
        routeDevMs = 0L
        lastHarshKey = ""
        lastSeatbeltWarn = false
        lastImpactWarn = false
        lastTsMs = 0L
        lastKey = ""
        _state.value = DriverScore.State()
    }

    fun tick(
        prefs: VePrefs,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (!prefs.driverScoreEnabled) {
            _state.value = DriverScore.State()
            lastTsMs = 0L
            return
        }

        val simScore = prefs.driverScoreSimScore
        val shift = ShiftTracker.shift.value
        val shiftOpen = shift.status == "open"
        if (shiftOpen && shift.id > 0L && shift.id != lastShiftId) {
            reset()
            lastShiftId = shift.id
        }

        val active = shiftOpen || simScore > 0f
        if (!active) {
            _state.value = DriverScore.State()
            lastTsMs = nowMs
            return
        }

        if (lastTsMs > 0L && shiftOpen) {
            val dt = (nowMs - lastTsMs).coerceIn(0L, 5_000L)
            val harsh = HarshDrivingMonitor.state.value
            if (harsh.showWarn && harsh.band != lastHarshKey) {
                lastHarshKey = harsh.band
                when (harsh.kind) {
                    "brake" -> harshBrakeN += 1
                    "accel" -> harshAccelN += 1
                }
            } else if (!harsh.showWarn) {
                lastHarshKey = ""
            }

            val hud = SpeedHudMonitor.state.value
            if (hud.band == "over") {
                overspeedMs += dt
            }

            val belt = SeatbeltMonitor.state.value
            if (belt.showWarn && !lastSeatbeltWarn) {
                seatbeltN += 1
            }
            lastSeatbeltWarn = belt.showWarn

            val impact = ImpactDetectMonitor.state.value
            if (impact.showWarn && !lastImpactWarn) {
                impactN += 1
            }
            lastImpactWarn = impact.showWarn

            val route = RouteDeviationMonitor.state.value
            if (route.band == "warn" || route.band == "alert") {
                routeDevMs += dt
            }
        }
        lastTsMs = nowMs

        val acc =
            DriverScore.Accumulators(
                harshBrakeEvents = harshBrakeN,
                harshAccelEvents = harshAccelN,
                overspeedSec = overspeedMs / 1000f,
                seatbeltEvents = seatbeltN,
                impactEvents = impactN,
                routeDevSec = routeDevMs / 1000f,
            )
        var st =
            DriverScore.evaluate(
                acc = acc,
                warnScore = prefs.driverScoreWarn.toInt(),
                alertScore = prefs.driverScoreAlert.toInt(),
                active = true,
            )
        if (simScore > 0f) {
            val sim = simScore.coerceIn(1f, 100f).toInt()
            val band =
                when {
                    sim >= 80 -> "good"
                    sim >= 60 -> "fair"
                    else -> "poor"
                }
            st =
                st.copy(
                    score = sim,
                    band = band,
                    showWarn = sim < prefs.driverScoreWarn,
                    label = "Score $sim · $band",
                    active = true,
                )
        }
        _state.value = st

        if (!st.showWarn) {
            lastKey = ""
            return
        }
        val cooled = nowMs - lastSpokenMs >= COOLDOWN_MS
        val key = "${st.band}:${st.score / 5}"
        val changed = key != lastKey
        if ((cooled || changed) && prefs.driverScoreTts) {
            lastSpokenMs = nowMs
            lastKey = key
            val phrase = DriverScore.voicePhrase(st)
            NavTts.speakNow(phrase)
            val kind =
                if (st.score <= prefs.driverScoreAlert) "score_alert" else "score_warn"
            FleetInbox.push(
                prefs = prefs,
                kind = kind,
                text = phrase,
                severity = if (kind == "score_alert") "critical" else "warn",
                id = "score:${st.band}:${nowMs / 300_000}",
                speak = false,
            )
        }
    }
}
