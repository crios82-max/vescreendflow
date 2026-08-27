package com.veplayer.app.vehicle

/**
 * Live eco score alerts during open shift (wraps EcoScore bands + warn/alert thresholds).
 */
object EcoLive {
    data class State(
        val score: Int = 100,
        /** good | fair | poor | idle */
        val band: String = "idle",
        val showWarn: Boolean = false,
        val label: String = "",
        val active: Boolean = false,
        val idleSec: Double = 0.0,
        val overspeedSec: Double = 0.0,
        val absEvents: Int = 0,
    )

    fun evaluate(
        score: Int?,
        band: String? = null,
        warnScore: Int = 70,
        alertScore: Int = 50,
        active: Boolean = true,
        idleSec: Double = 0.0,
        overspeedSec: Double = 0.0,
        absEvents: Int = 0,
    ): State {
        if (!active || score == null) {
            return State(band = "idle", active = false)
        }
        val s = score.coerceIn(0, 100)
        val warn = warnScore.coerceIn(30, 95)
        val alert = alertScore.coerceIn(10, warn - 1)
        val b =
            band?.takeIf { it in listOf("good", "fair", "poor") }
                ?: when {
                    s >= 80 -> "good"
                    s >= 55 -> "fair"
                    else -> "poor"
                }
        val showWarn = s < warn
        return State(
            score = s,
            band = b,
            showWarn = showWarn,
            label = "Eco $s · $b",
            active = true,
            idleSec = idleSec,
            overspeedSec = overspeedSec,
            absEvents = absEvents,
        ).let { st ->
            if (s <= alert) st.copy(showWarn = true) else st
        }
    }

    fun voicePhrase(st: State): String =
        when {
            st.score <= 50 || st.band == "poor" ->
                "Atención. Puntaje eco bajo. ${st.score} puntos. Conduce más eficiente."
            st.showWarn || st.band == "fair" ->
                "Cuidado. Tu eco bajó a ${st.score}. Reduce ralentí y excesos."
            else -> EcoScore.voicePhrase(
                EcoScore.Result(
                    score = st.score,
                    band = st.band,
                    idleSec = st.idleSec,
                    overspeedSec = st.overspeedSec,
                    absEvents = st.absEvents,
                    highThrottleSec = 0.0,
                    distanceKm = 0.0,
                    penalties = emptyMap(),
                ),
            )
        }

    fun toJsonMap(st: State): Map<String, Any?> =
        mapOf(
            "score" to st.score,
            "band" to st.band,
            "show_warn" to st.showWarn,
            "active" to st.active,
            "idle_sec" to st.idleSec,
            "overspeed_sec" to st.overspeedSec,
            "abs_events" to st.absEvents,
        )
}
