package com.veplayer.app.vehicle

/**
 * Collision / impact candidate from extreme decel or yaw spike.
 * Thresholds sit above normal harsh brake/accel.
 */
object ImpactDetect {
    data class State(
        val decelKmhS: Float = 0f,
        val yawDegS: Float = 0f,
        val speedKmh: Float = 0f,
        /** Approximate g from longitudinal decel. */
        val gApprox: Float = 0f,
        /** ok | warn | alert | idle */
        val band: String = "idle",
        val kind: String = "", // decel | yaw | ""
        val showWarn: Boolean = false,
        val label: String = "",
    )

    fun evaluate(
        decelKmhS: Float,
        yawDegS: Float,
        speedKmh: Float,
        decelWarn: Float = 28f,
        decelAlert: Float = 40f,
        yawWarn: Float = 80f,
        yawAlert: Float = 120f,
        speedMinKmh: Float = 8f,
    ): State {
        val speed = speedKmh.coerceAtLeast(0f)
        val decel = decelKmhS.coerceAtLeast(0f)
        val yaw = kotlin.math.abs(yawDegS)
        if (speed < speedMinKmh && decel < decelWarn && yaw < yawWarn) {
            return State(speedKmh = speed, band = "idle")
        }
        val dWarn = decelWarn.coerceIn(15f, 60f)
        val dAlert = decelAlert.coerceAtLeast(dWarn + 1f)
        val yWarn = yawWarn.coerceIn(40f, 200f)
        val yAlert = yawAlert.coerceAtLeast(yWarn + 1f)
        val g = (decel / 3.6f) / 9.81f

        val decelBand =
            when {
                decel >= dAlert -> "alert"
                decel >= dWarn -> "warn"
                else -> "ok"
            }
        val yawBand =
            when {
                yaw >= yAlert -> "alert"
                yaw >= yWarn -> "warn"
                else -> "ok"
            }
        val band =
            when {
                decelBand == "alert" || yawBand == "alert" -> "alert"
                decelBand == "warn" || yawBand == "warn" -> "warn"
                else -> "ok"
            }
        if (band == "ok") {
            return State(
                decelKmhS = decel,
                yawDegS = yaw,
                speedKmh = speed,
                gApprox = g,
                band = "ok",
            )
        }
        val kindFinal =
            when {
                decelBand == "alert" && yawBand != "alert" -> "decel"
                yawBand == "alert" && decelBand != "alert" -> "yaw"
                decel >= yaw / 2f -> "decel"
                else -> "yaw"
            }
        val label =
            when (kindFinal) {
                "yaw" -> "Impacto · yaw ${yaw.toInt()}°/s"
                else -> "Impacto · ${decel.toInt()} km/h/s"
            }
        return State(
            decelKmhS = decel,
            yawDegS = yaw,
            speedKmh = speed,
            gApprox = g,
            band = band,
            kind = kindFinal,
            showWarn = true,
            label = label,
        )
    }

    fun voicePhrase(st: State): String =
        when (st.band) {
            "alert" ->
                when (st.kind) {
                    "yaw" -> "Atención. Posible impacto. Giro brusco detectado."
                    else -> "Atención. Posible impacto. Desaceleración extrema."
                }
            "warn" ->
                when (st.kind) {
                    "yaw" -> "Cuidado. Maniobra violenta. Posible golpe."
                    else -> "Cuidado. Desaceleración extrema. Posible impacto."
                }
            else -> ""
        }

    fun accentArgb(band: String): Long =
        when (band) {
            "alert" -> 0xFFE11D48
            "warn" -> 0xFFF97316
            else -> 0xFF94A3B8
        }

    fun toJsonMap(st: State): Map<String, Any?> =
        mapOf(
            "band" to st.band,
            "kind" to st.kind.ifBlank { null },
            "decel_kmh_s" to st.decelKmhS.toDouble(),
            "yaw_deg_s" to st.yawDegS.toDouble(),
            "speed_kmh" to st.speedKmh.toDouble(),
            "g_approx" to (Math.round(st.gApprox * 100.0) / 100.0),
            "show_warn" to st.showWarn,
        )
}
