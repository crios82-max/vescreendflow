package com.veplayer.app.fleet

import com.veplayer.app.data.VePrefs
import com.veplayer.app.nav.NavTts
import java.util.concurrent.ConcurrentLinkedDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class FleetInboxItem(
    val id: String,
    val kind: String,
    val severity: String,
    val text: String,
    val tsMs: Long = System.currentTimeMillis(),
)

/**
 * Persistent ring of fleet messages/alerts + optional TTS (NavTts).
 */
object FleetInbox {
    private const val MAX = 40
    private val spokenAlertIds = LinkedHashSet<Long>()
    private val deque = ConcurrentLinkedDeque<FleetInboxItem>()

    private val _itemsFlow = MutableStateFlow<List<FleetInboxItem>>(emptyList())
    val items: StateFlow<List<FleetInboxItem>> = _itemsFlow.asStateFlow()

    private val _last = MutableStateFlow<FleetInboxItem?>(null)
    val last: StateFlow<FleetInboxItem?> = _last.asStateFlow()

    @Synchronized
    fun load(prefs: VePrefs) {
        val raw = prefs.fleetInboxJson
        if (raw.isBlank()) return
        runCatching {
            val arr = JSONArray(raw)
            deque.clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                deque.addLast(
                    FleetInboxItem(
                        id = o.optString("id"),
                        kind = o.optString("kind"),
                        severity = o.optString("severity", "info"),
                        text = o.optString("text"),
                        tsMs = o.optLong("ts", System.currentTimeMillis()),
                    ),
                )
            }
            publish()
        }
    }

    fun push(
        prefs: VePrefs,
        kind: String,
        text: String,
        severity: String = "info",
        id: String = "${kind}:${System.currentTimeMillis()}",
        speak: Boolean = false,
    ) {
        val item =
            FleetInboxItem(
                id = id,
                kind = kind,
                severity = severity,
                text = text.trim(),
            )
        if (item.text.isBlank()) return
        synchronized(this) {
            deque.removeIf { it.id == item.id }
            deque.addFirst(item)
            while (deque.size > MAX) deque.removeLast()
            persist(prefs)
            publish()
        }
        if (speak) {
            NavTts.speakNow(voicePhrase(kind, severity, item.text))
        }
    }

    fun onAlerts(
        prefs: VePrefs,
        alerts: List<FleetAlert>,
    ): List<FleetInboxItem> {
        if (!prefs.fleetAlertsEnabled) return emptyList()
        val fresh = mutableListOf<FleetInboxItem>()
        for (a in alerts) {
            val key = "alert:${a.id}"
            val firstTime =
                synchronized(spokenAlertIds) {
                    if (a.id in spokenAlertIds) {
                        false
                    } else {
                        spokenAlertIds += a.id
                        while (spokenAlertIds.size > 120) {
                            val first = spokenAlertIds.firstOrNull() ?: break
                            spokenAlertIds.remove(first)
                        }
                        true
                    }
                }
            if (!firstTime) continue
            push(
                prefs = prefs,
                kind = a.kind,
                text = a.message,
                severity = a.severity,
                id = key,
                speak = prefs.fleetTtsAlerts,
            )
            fresh +=
                FleetInboxItem(
                    id = key,
                    kind = a.kind,
                    severity = a.severity,
                    text = a.message,
                )
        }
        return fresh
    }

    fun onDispatchMessage(
        prefs: VePrefs,
        text: String,
    ) {
        push(
            prefs = prefs,
            kind = "message",
            text = text,
            severity = "info",
            speak = prefs.fleetTtsMessages,
        )
    }

    fun voicePhrase(
        kind: String,
        severity: String,
        text: String,
    ): String {
        val body = text.trim().trimEnd('.')
        return when {
            kind.startsWith("geofence_speed") || kind == "geofence_speed" ->
                "Atención. Exceso en zona. $body."
            kind.startsWith("geofence") -> "Alerta de zona. $body."
            kind == "abs" -> "Atención. Sistema ABS activo."
            kind == "tpms_low" -> "Atención. Presión de neumáticos baja."
            kind == "mil_on" || kind == "mil" -> "Atención. Luz de motor encendida."
            kind == "parking_crit" || kind == "parking_near" || kind.startsWith("parking_") ->
                "Atención. Estacionamiento. $body."
            kind == "door_moving" -> "Atención. Puerta abierta en movimiento. $body."
            kind == "door_ajar" || kind.startsWith("door_") ->
                "Cuidado. Puerta abierta. $body."
            kind == "shift_fatigue" -> "Atención. Turno prolongado. $body."
            kind == "shift_warn" || kind.startsWith("shift_") ->
                "Cuidado. Duración de turno. $body."
            kind.startsWith("dtc:") || kind == "dtc" ->
                "Atención. Código de falla. $body."
            kind == "soc_low" -> "Atención. Batería baja. $body."
            kind == "fuel_low" -> "Atención. Combustible bajo. $body."
            kind == "range_low" -> "Atención. Autonomía baja. $body."
            kind == "idle_alert" || kind == "idle_warn" || kind.startsWith("idle_") ->
                "Atención. Motor en ralentí. $body."
            kind == "panic" -> "Emergencia. SOS enviado. $body."
            kind == "message" -> "Mensaje de flota. $body."
            severity == "critical" -> "Emergencia. $body."
            severity == "warn" -> "Alerta. $body."
            else -> "Aviso de flota. $body."
        }
    }

    private fun persist(prefs: VePrefs) {
        val arr = JSONArray()
        for (it in deque) {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("kind", it.kind)
                    .put("severity", it.severity)
                    .put("text", it.text)
                    .put("ts", it.tsMs),
            )
        }
        prefs.fleetInboxJson = arr.toString()
    }

    private fun publish() {
        val list = deque.toList()
        _itemsFlow.value = list
        _last.value = list.firstOrNull()
    }
}
