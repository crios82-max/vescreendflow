package com.veplayer.app.vehicle

/**
 * Eco / trip scorecard math (0–100). Pure — mirrored in SenseFlow + smoke.
 */
object EcoScore {
    data class Accumulators(
        val idleSec: Double = 0.0,
        val overspeedSec: Double = 0.0,
        val absEvents: Int = 0,
        val highThrottleSec: Double = 0.0,
        val distanceKm: Double = 0.0,
    )

    data class Result(
        val score: Int,
        val band: String, // good | fair | poor
        val idleSec: Double,
        val overspeedSec: Double,
        val absEvents: Int,
        val highThrottleSec: Double,
        val distanceKm: Double,
        val penalties: Map<String, Int>,
    )

    fun evaluate(acc: Accumulators): Result {
        val idlePen = minOf(30, (acc.idleSec / 60.0 * 2.0).toInt())
        val overPen = minOf(40, (acc.overspeedSec / 8.0).toInt())
        val absPen = minOf(20, acc.absEvents * 5)
        val thrPen = minOf(20, (acc.highThrottleSec / 15.0).toInt())
        val score = (100 - idlePen - overPen - absPen - thrPen).coerceIn(0, 100)
        val band =
            when {
                score >= 80 -> "good"
                score >= 55 -> "fair"
                else -> "poor"
            }
        return Result(
            score = score,
            band = band,
            idleSec = acc.idleSec,
            overspeedSec = acc.overspeedSec,
            absEvents = acc.absEvents,
            highThrottleSec = acc.highThrottleSec,
            distanceKm = acc.distanceKm,
            penalties =
                mapOf(
                    "idle" to idlePen,
                    "overspeed" to overPen,
                    "abs" to absPen,
                    "throttle" to thrPen,
                ),
        )
    }

    fun voicePhrase(r: Result): String =
        when (r.band) {
            "good" -> "Conducción eficiente. Puntaje ${r.score}."
            "fair" -> "Conducción aceptable. Puntaje ${r.score}."
            else -> "Conducción a mejorar. Puntaje ${r.score}."
        }

    fun accentArgb(band: String): Long =
        when (band) {
            "good" -> 0xFF10B981
            "fair" -> 0xFFF59E0B
            else -> 0xFFE11D48
        }
}
