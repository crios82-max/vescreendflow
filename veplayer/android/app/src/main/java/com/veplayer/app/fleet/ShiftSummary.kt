package com.veplayer.app.fleet

/**
 * End-of-shift digest for HUD / TTS.
 */
object ShiftSummary {
    data class State(
        val shiftId: Long = 0,
        val durationSec: Long = 0,
        val durationLabel: String = "",
        val distanceKm: Double = 0.0,
        val ecoScore: Int? = null,
        val ecoBand: String = "",
        val idleMin: Int = 0,
        val overspeedMin: Int = 0,
        val absEvents: Int = 0,
        val driverName: String = "",
        val message: String = "",
        val label: String = "",
        val show: Boolean = false,
    )

    fun fromJson(o: org.json.JSONObject?): State {
        if (o == null || o === org.json.JSONObject.NULL) return State()
        val durationSec = o.optLong("duration_sec", 0L)
        val durationLabel =
            o.optString("duration_label").ifBlank {
                if (durationSec >= 3600) {
                    "${"%.1f".format(durationSec / 3600.0)} h"
                } else {
                    "${(durationSec / 60).coerceAtLeast(1)} min"
                }
            }
        val distanceKm = o.optDouble("distance_km", 0.0)
        val eco =
            if (o.has("eco_score") && !o.isNull("eco_score")) o.optInt("eco_score") else null
        val ecoBand = o.optString("eco_band", "")
        val idleMin =
            if (o.has("idle_min")) {
                o.optInt("idle_min")
            } else {
                (o.optDouble("idle_sec", 0.0) / 60).toInt()
            }
        val overMin =
            if (o.has("overspeed_min")) {
                o.optInt("overspeed_min")
            } else {
                (o.optDouble("overspeed_sec", 0.0) / 60).toInt()
            }
        val absEvents = o.optInt("abs_events", 0)
        val shiftId = o.optLong("shift_id", o.optLong("id", 0))
        val message =
            o.optString("message").ifBlank {
                listOfNotNull(
                    "Turno #$shiftId",
                    durationLabel,
                    "${"%.1f".format(distanceKm)} km",
                    eco?.let { "eco $it" },
                ).joinToString(" · ")
            }
        val label =
            buildString {
                append("Resumen · ")
                append(durationLabel)
                append(" · ")
                append("%.1f".format(distanceKm))
                append(" km")
                if (eco != null) {
                    append(" · eco ")
                    append(eco)
                }
            }
        return State(
            shiftId = shiftId,
            durationSec = durationSec,
            durationLabel = durationLabel,
            distanceKm = distanceKm,
            ecoScore = eco,
            ecoBand = ecoBand,
            idleMin = idleMin,
            overspeedMin = overMin,
            absEvents = absEvents,
            driverName = o.optString("driver_name", ""),
            message = message,
            label = label,
            show = shiftId > 0 || distanceKm > 0 || durationSec > 0,
        )
    }

    fun fromShift(s: ShiftSnapshot): State {
        if (s.status != "closed" && s.id <= 0) return State()
        val durationSec =
            if (s.startedAt > 0) {
                ((System.currentTimeMillis() - s.startedAt) / 1000L).coerceAtLeast(0)
            } else {
                0L
            }
        val durationLabel =
            if (durationSec >= 3600) {
                "${"%.1f".format(durationSec / 3600.0)} h"
            } else {
                "${(durationSec / 60).coerceAtLeast(1)} min"
            }
        val message =
            listOfNotNull(
                "Turno #${s.id}",
                durationLabel,
                "${"%.1f".format(s.distanceKm)} km",
                s.ecoScore?.let { "eco $it" },
            ).joinToString(" · ")
        return State(
            shiftId = s.id,
            durationSec = durationSec,
            durationLabel = durationLabel,
            distanceKm = s.distanceKm,
            ecoScore = s.ecoScore,
            ecoBand = s.ecoBand,
            idleMin = (s.idleSec / 60).toInt(),
            overspeedMin = (s.overspeedSec / 60).toInt(),
            message = message,
            label = "Resumen · $durationLabel · ${"%.1f".format(s.distanceKm)} km" +
                (s.ecoScore?.let { " · eco $it" } ?: ""),
            show = true,
        )
    }

    fun voicePhrase(st: State): String {
        val km = "%.1f".format(st.distanceKm)
        val eco =
            st.ecoScore?.let {
                val band =
                    when (st.ecoBand) {
                        "good" -> "buena"
                        "poor" -> "baja"
                        "fair" -> "regular"
                        else -> st.ecoBand.ifBlank { "" }
                    }
                if (band.isNotBlank()) " Eco $it, $band." else " Eco $it."
            } ?: ""
        val idle =
            if (st.idleMin > 0) " Ralentí ${st.idleMin} minutos." else ""
        return "Turno cerrado. ${st.durationLabel}. $km kilómetros.$eco$idle"
    }

    fun accentArgb(): Long = 0xFF38BDF8
}
