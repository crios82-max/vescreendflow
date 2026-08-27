package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watches odometer vs service intervals; TTS + inbox when due/warn.
 */
object MaintenanceMonitor {
    data class Snapshot(
        val odoKm: Float?,
        val due: Int = 0,
        val warn: Int = 0,
        val items: List<Maintenance.Status> = emptyList(),
    )

    private val _state = MutableStateFlow(Snapshot(null))
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    private val spokenKeys = LinkedHashSet<String>()
    private var lastTickMs = 0L
    private const val MIN_TICK_MS = 2_000L

    fun tick(
        prefs: VePrefs,
        odoKm: Float?,
    ) {
        val now = System.currentTimeMillis()
        if (now - lastTickMs < MIN_TICK_MS) return
        lastTickMs = now

        if (!prefs.maintenanceEnabled) {
            _state.value = Snapshot(odoKm)
            return
        }
        val items = Maintenance.parseJson(prefs.maintenanceJson)
        val statuses = Maintenance.evaluateAll(items, odoKm)
        val due = statuses.count { it.band == "due" }
        val warn = statuses.count { it.band == "warn" }
        _state.value = Snapshot(odoKm, due, warn, statuses)

        for (st in statuses) {
            if (st.band != "due" && st.band != "warn") continue
            val bucket = now / (6 * 3_600_000L)
            val key = "${st.band}:${st.item.kind}:$bucket"
            val first =
                synchronized(spokenKeys) {
                    if (key in spokenKeys) {
                        false
                    } else {
                        spokenKeys += key
                        while (spokenKeys.size > 40) {
                            val first = spokenKeys.firstOrNull() ?: break
                            spokenKeys.remove(first)
                        }
                        true
                    }
                }
            if (!first) continue
            val phrase = Maintenance.voicePhrase(st)
            if (prefs.maintenanceTts) {
                NavTts.speakNow(phrase)
            }
            FleetInbox.push(
                prefs = prefs,
                kind = "maint_${st.band}",
                text = phrase,
                severity = if (st.band == "due") "warn" else "info",
                id = "maint:${st.item.kind}:${st.band}:$bucket",
                speak = false,
            )
        }
    }
}
