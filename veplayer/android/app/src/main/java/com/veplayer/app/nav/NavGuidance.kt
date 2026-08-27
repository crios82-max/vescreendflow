package com.veplayer.app.nav

/**
 * Pure nav guidance: which step/cue to announce from ego + route.
 * Shared contract with `veplayer/scripts/nav-tts-smoke.mjs`.
 */
object NavGuidance {
    /** Distance bands (m) — announce once per step when crossing into band. */
    val THRESHOLDS_M = intArrayOf(800, 400, 150, 50)

    data class Cue(
        val key: String,
        val phrase: String,
        val stepIndex: Int,
        val remainM: Float,
        val thresholdM: Int?,
    )

    fun currentStepIndex(
        route: NavRoute,
        ego: LatLng,
    ): Int {
        if (route.steps.isEmpty()) return -1
        val path = route.geometry.map { LatLng(it.first, it.second) }
        val progress =
            if (path.size >= 2) {
                GeoProjection.progressAlong(path, ego)
            } else {
                0f
            }
        val traveled = progress * route.distanceM.coerceAtLeast(0.0)
        var cum = 0.0
        for (i in route.steps.indices) {
            cum += route.steps[i].distanceM
            if (traveled < cum - 8.0) return i
        }
        return route.steps.lastIndex
    }

    fun remainOnStepM(
        route: NavRoute,
        ego: LatLng,
        stepIndex: Int,
    ): Float {
        if (stepIndex < 0 || stepIndex >= route.steps.size) return 0f
        val path = route.geometry.map { LatLng(it.first, it.second) }
        val progress =
            if (path.size >= 2) {
                GeoProjection.progressAlong(path, ego)
            } else {
                0f
            }
        val traveled = progress * route.distanceM.coerceAtLeast(0.0)
        var before = 0.0
        for (i in 0 until stepIndex) before += route.steps[i].distanceM
        val into = (traveled - before).coerceAtLeast(0.0)
        return (route.steps[stepIndex].distanceM - into).toFloat().coerceAtLeast(0f)
    }

    fun formatDistanceM(m: Float): String {
        if (m >= 1000f) {
            val tenths = kotlin.math.round(m / 100f).toInt() // 0.1 km units
            return if (tenths % 10 == 0) {
                val km = tenths / 10
                if (km == 1) "1 kilómetro" else "$km kilómetros"
            } else {
                val s = "${tenths / 10},${tenths % 10}"
                "$s kilómetros"
            }
        }
        val rounded = (kotlin.math.round(m / 10.0) * 10).toInt().coerceAtLeast(10)
        return "$rounded metros"
    }

    fun routeIntro(
        route: NavRoute,
    ): Cue? {
        if (route.destinationName.isBlank() || route.distanceM <= 0) return null
        val dest = route.destinationName
        val dur = route.durationLabel
        val phrase = "Ruta hacia $dest. Tiempo estimado $dur."
        return Cue(
            key = "intro:${dest}:${route.distanceM.toInt()}",
            phrase = phrase,
            stepIndex = 0,
            remainM = route.distanceM.toFloat(),
            thresholdM = null,
        )
    }

    /**
     * Next voice cue for [stepIndex] at [remainM], skipping keys already in [spoken].
     */
    fun nextCue(
        route: NavRoute,
        stepIndex: Int,
        remainM: Float,
        spoken: Set<String>,
        destKey: String,
    ): Cue? {
        if (stepIndex < 0 || stepIndex >= route.steps.size) return null
        val step = route.steps[stepIndex]
        val instr = step.instruction.trim().ifBlank { "Continuar" }

        if (step.type.equals("arrive", ignoreCase = true) ||
            instr.contains("Llegaste", ignoreCase = true)
        ) {
            val key = "arrive:$destKey"
            if (key in spoken) return null
            if (remainM > 80f && stepIndex < route.steps.lastIndex) return null
            return Cue(
                key = key,
                phrase = if (route.destinationName.isNotBlank()) {
                    "Llegaste a ${route.destinationName}."
                } else {
                    "Llegaste al destino."
                },
                stepIndex = stepIndex,
                remainM = remainM,
                thresholdM = 0,
            )
        }

        // Tightest threshold we are inside and haven't spoken
        val band =
            THRESHOLDS_M.filter { remainM <= it }.minOrNull() ?: run {
                // Far away: announce once at start of step with remaining distance
                if (remainM > THRESHOLDS_M.first()) {
                    val key = "step:$destKey:$stepIndex:start"
                    if (key in spoken) return null
                    return Cue(
                        key = key,
                        phrase = phraseFor(instr, remainM),
                        stepIndex = stepIndex,
                        remainM = remainM,
                        thresholdM = null,
                    )
                }
                return null
            }

        val key = "step:$destKey:$stepIndex:t$band"
        if (key in spoken) return null
        // Also mark coarser bands as spoken so we don't replay them after the fact
        return Cue(
            key = key,
            phrase = phraseFor(instr, remainM),
            stepIndex = stepIndex,
            remainM = remainM,
            thresholdM = band,
        )
    }

    fun phraseFor(
        instruction: String,
        remainM: Float,
    ): String {
        val instr = instruction.trim().trimEnd('.')
        if (remainM <= 55f) return "$instr."
        return "En ${formatDistanceM(remainM)}, ${instr.replaceFirstChar { it.lowercase() }}."
    }

    /** Coarser keys to suppress after speaking a tight band. */
    fun suppressKeysFor(
        cue: Cue,
        destKey: String,
    ): Set<String> {
        val out = mutableSetOf(cue.key)
        if (cue.thresholdM != null) {
            for (t in THRESHOLDS_M) {
                if (t >= cue.thresholdM) {
                    out += "step:$destKey:${cue.stepIndex}:t$t"
                }
            }
            out += "step:$destKey:${cue.stepIndex}:start"
        }
        return out
    }
}
