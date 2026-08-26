package com.veplayer.app.surround

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ActorKind {
    PERSON,
    MOTORCYCLE,
    BICYCLE,
    CAR,
    TRUCK,
    BUS,
    UNKNOWN,
}

data class SurroundActor(
    val id: String,
    val kind: ActorKind,
    /** meters right of ego (+) */
    val xM: Float,
    /** meters ahead of ego (+) */
    val yM: Float,
    val speedMps: Float = 0f,
    val source: String = "unknown",
    val confidence: Float = 1f,
)

data class SurroundSnapshot(
    val actors: List<SurroundActor> = emptyList(),
    val updatedAtMs: Long = 0L,
)

/**
 * Merges SenseFlow crowd/traffic pings + camera vision into one Tesla-like surround model.
 */
object SurroundEngine {
    private val sense = MutableStateFlow<List<SurroundActor>>(emptyList())
    private val vision = MutableStateFlow<List<SurroundActor>>(emptyList())
    private val _snapshot = MutableStateFlow(SurroundSnapshot())
    val snapshot: StateFlow<SurroundSnapshot> = _snapshot.asStateFlow()

    fun publishSenseflow(actors: List<SurroundActor>) {
        sense.value = actors
        recompute()
    }

    fun publishVision(actors: List<SurroundActor>) {
        vision.value = actors
        recompute()
    }

    private fun recompute() {
        // Vision wins in near field (<25m), SenseFlow fills farther / gaps
        val nearVision = vision.value.filter { it.yM in 2f..40f && kotlin.math.abs(it.xM) < 12f }
        val farSense =
            sense.value.filter { s ->
                nearVision.none { v -> kotlin.math.hypot((v.xM - s.xM).toDouble(), (v.yM - s.yM).toDouble()) < 4.0 }
            }
        _snapshot.update {
            SurroundSnapshot(
                actors = (nearVision + farSense).sortedByDescending { it.yM },
                updatedAtMs = System.currentTimeMillis(),
            )
        }
    }
}
