package com.veplayer.app.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Active MIL / DTC snapshot (live OBD poll or sim seed).
 */
object DtcBus {
    private val _snap = MutableStateFlow(ObdDtc.Snapshot())
    val snap: StateFlow<ObdDtc.Snapshot> = _snap.asStateFlow()

    fun apply(snapshot: ObdDtc.Snapshot) {
        _snap.value = snapshot
    }

    fun seedDemo() {
        _snap.value = ObdDtc.demoSeed()
    }

    fun seed(
        codes: List<ObdDtc.Code>,
        mil: Boolean = codes.isNotEmpty(),
    ) {
        _snap.value =
            ObdDtc.Snapshot(
                mil = mil,
                dtcCount = codes.size.coerceAtLeast(if (mil) 1 else 0),
                codes = codes,
            )
    }

    fun clear() {
        _snap.value = ObdDtc.Snapshot()
    }
}
