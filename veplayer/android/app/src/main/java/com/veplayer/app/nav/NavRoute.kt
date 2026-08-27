package com.veplayer.app.nav

data class NavLeg(
    val distanceM: Double,
    val durationS: Double,
    val toName: String,
)

data class NavWaypoint(
    val name: String,
    val lat: Double,
    val lng: Double,
    /** via | dest */
    val role: String = "via",
)

/** Live navigation guidance for cockpit chrome. */
data class NavStep(
    val instruction: String,
    val distanceM: Float,
    val name: String = "",
    val type: String = "",
    val modifier: String = "",
)

data class NavRoute(
    val distanceM: Double = 0.0,
    val durationS: Double = 0.0,
    val destinationName: String = "",
    val steps: List<NavStep> = emptyList(),
    val geometry: List<Pair<Double, Double>> = emptyList(), // lat,lng
    /** Intermediate + final stops. */
    val waypoints: List<NavWaypoint> = emptyList(),
    val legs: List<NavLeg> = emptyList(),
    val source: String = "idle",
    val updatedAtMs: Long = 0L,
) {
    val viaCount: Int
        get() = waypoints.count { it.role == "via" }

    val etaLabel: String
        get() {
            if (durationS <= 0) return "—"
            val arrive = System.currentTimeMillis() + (durationS * 1000).toLong()
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = arrive }
            return "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
        }

    val durationLabel: String
        get() {
            val m = (durationS / 60.0).toInt().coerceAtLeast(1)
            return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
        }

    val distanceLabel: String
        get() =
            if (distanceM >= 1000) "%.1f km".format(distanceM / 1000.0).replace('.', ',')
            else "${distanceM.toInt()} m"

    val nextBanner: String
        get() {
            val s = steps.firstOrNull() ?: return "Sin ruta"
            val dist =
                if (s.distanceM >= 1000) "%.1f km".format(s.distanceM / 1000f)
                else "${s.distanceM.toInt()} m"
            return "$dist · ${s.instruction}"
        }

    val nextDistanceShort: String
        get() {
            val s = steps.firstOrNull() ?: return "—"
            return if (s.distanceM >= 1000) "%.1f km".format(s.distanceM / 1000f)
            else "${s.distanceM.toInt()} m"
        }

    val nextInstruction: String
        get() = steps.firstOrNull()?.instruction ?: "Sin navegación"

    val stopsLabel: String
        get() {
            val vias = waypoints.filter { it.role == "via" }
            return when {
                vias.isEmpty() -> destinationName.ifBlank { "—" }
                else -> vias.joinToString(" → ") { it.name } + " → " + destinationName
            }
        }
}
