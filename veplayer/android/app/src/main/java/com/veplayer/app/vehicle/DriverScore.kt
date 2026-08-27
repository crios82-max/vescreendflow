package com.veplayer.app.vehicle

/**
 * Safety driver scorecard (0–100) for the current shift/session.
 * Pure — mirrored in SenseFlow + smoke. Separate from EcoScore.
 */
object DriverScore {
    data class Accumulators(
        val harshBrakeEvents: Int = 0,
        val harshAccelEvents: Int = 0,
        val overspeedSec: Float = 0f,
        val seatbeltEvents: Int = 0,
        val impactEvents: Int = 0,
        val routeDevSec: Float = 0f,
    )

    data class State(
        val score: Int = 100,
        /** good | fair | poor | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
        val harshBrakeEvents: Int = 0,
        val harshAccelEvents: Int = 0,
        val overspeedSec: Float = 0f,
        val seatbeltEvents: Int = 0,
        val impactEvents: Int = 0,
        val routeDevSec: Float = 0f,
        val penalties: Map<String, Int> = emptyMap(),
        val active: Boolean = false,
    )

    fun evaluate(
        acc: Accumulators,
        warnScore: Int = 70,
        alertScore: Int = 50,
        active: Boolean = true,
    ): State {
        if (!active) {
            return State(band = "idle", active = false)
        }
        val brakePen = minOf(25, acc.harshBrakeEvents * 5)
        val accelPen = minOf(20, acc.harshAccelEvents * 4)
        val overPen = minOf(25, (acc.overspeedSec / 12f).toInt())
        val beltPen = minOf(24, acc.seatbeltEvents * 8)
        val impactPen = minOf(24, acc.impactEvents * 12)
        val routePen = minOf(15, (acc.routeDevSec / 40f).toInt())
        val score =
            (100 - brakePen - accelPen - overPen - beltPen - impactPen - routePen)
                .coerceIn(0, 100)
        val warn = warnScore.coerceIn(30, 95)
        val alert = alertScore.coerceIn(10, warn - 1)
        val band =
            when {
                score >= 80 -> "good"
                score >= 60 -> "fair"
                else -> "poor"
            }
        val showWarn = score < warn
        val label = "Score $score · $band"
        return State(
            score = score,
            band = band,
            showWarn = showWarn,
            label = label,
            harshBrakeEvents = acc.harshBrakeEvents.coerceAtLeast(0),
            harshAccelEvents = acc.harshAccelEvents.coerceAtLeast(0),
            overspeedSec = acc.overspeedSec.coerceAtLeast(0f),
            seatbeltEvents = acc.seatbeltEvents.coerceAtLeast(0),
            impactEvents = acc.impactEvents.coerceAtLeast(0),
            routeDevSec = acc.routeDevSec.coerceAtLeast(0f),
            penalties =
                mapOf(
                    "harsh_brake" to brakePen,
                    "harsh_accel" to accelPen,
                    "overspeed" to overPen,
                    "seatbelt" to beltPen,
                    "impact" to impactPen,
                    "route" to routePen,
                ),
            active = true,
        ).let { st ->
            // Escalate showWarn messaging via band vs numeric thresholds for TTS kind
            if (score <= alert) st.copy(showWarn = true)
            else st
        }
    }

    fun voicePhrase(st: State): String =
        when {
            st.score <= 50 || st.band == "poor" ->
                "Atención. Puntaje de conducción bajo. ${st.score} puntos."
            st.showWarn || st.band == "fair" ->
                "Cuidado. Tu puntaje bajó a ${st.score}. Conduce con más calma."
            else -> "Puntaje de conducción ${st.score}. Buen trabajo."
        }

    fun accentArgb(band: String): Long =
        when (band) {
            "good" -> 0xFF10B981
            "fair" -> 0xFFF59E0B
            "poor" -> 0xFFE11D48
            else -> 0xFF94A3B8
        }

    fun toJsonMap(st: State): Map<String, Any?> =
        mapOf(
            "score" to st.score,
            "band" to st.band,
            "show_warn" to st.showWarn,
            "active" to st.active,
            "harsh_brake_events" to st.harshBrakeEvents,
            "harsh_accel_events" to st.harshAccelEvents,
            "overspeed_sec" to st.overspeedSec.toDouble(),
            "seatbelt_events" to st.seatbeltEvents,
            "impact_events" to st.impactEvents,
            "route_dev_sec" to st.routeDevSec.toDouble(),
            "penalties" to st.penalties,
        )
}
