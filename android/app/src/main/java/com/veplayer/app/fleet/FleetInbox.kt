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
            // Replies are for ops; don't re-offer / re-speak on the unit
            if (a.kind == "message_reply") continue
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
        alertId: Long? = null,
        commandId: Long? = null,
        requiresAck: Boolean = true,
    ) {
        push(
            prefs = prefs,
            kind = "message",
            text = text,
            severity = "info",
            id = if (alertId != null && alertId > 0) "alert:$alertId" else "message:${System.currentTimeMillis()}",
            speak = prefs.fleetTtsMessages,
        )
        if (prefs.messageReplyEnabled && alertId != null && alertId > 0) {
            MessageReplyBus.offer(
                alertId = alertId,
                text = text,
                commandId = commandId,
                requiresAck = requiresAck,
            )
        }
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
            kind.startsWith("geofence_exit") -> "Saliste de la zona. $body."
            kind.startsWith("geofence_enter") -> "Entraste a una zona. $body."
            kind.startsWith("geofence") -> "Alerta de zona. $body."
            kind == "abs" -> "Atención. Sistema ABS activo."
            kind == "tpms_alert" -> "Atención. Presión crítica de neumáticos. $body."
            kind == "tpms_warn" || kind == "tpms_low" || kind.startsWith("tpms_") ->
                "Cuidado. Presión de neumáticos baja. $body."
            kind == "mil_on" || kind == "mil" -> "Atención. Luz de motor encendida."
            kind == "parking_crit" || kind == "parking_near" || kind.startsWith("parking_") ->
                "Atención. Estacionamiento. $body."
            kind == "door_moving" -> "Atención. Puerta abierta en movimiento. $body."
            kind == "door_ajar" || kind.startsWith("door_") ->
                "Cuidado. Puerta abierta. $body."
            kind == "seatbelt_alert" -> "Atención. Abróchate el cinturón. $body."
            kind == "seatbelt_warn" || kind.startsWith("seatbelt_") ->
                "Cuidado. Cinturón desabrochado. $body."
            kind == "brake_alert" || kind == "brake_warn" ->
                "Atención. Frenada. $body."
            kind == "accel_alert" || kind == "accel_warn" ->
                "Atención. Aceleración. $body."
            kind == "impact_alert" -> "Atención. Posible impacto. $body."
            kind == "impact_warn" || kind.startsWith("impact_") ->
                "Cuidado. Maniobra extrema. $body."
            kind == "rest_break" -> "Atención. Es hora de un descanso. $body."
            kind == "rest_warn" || kind.startsWith("rest_") ->
                "Cuidado. Pausa recomendada. $body."
            kind == "route_deviate" -> "Atención. Fuera de ruta. $body."
            kind == "route_warn" || kind.startsWith("route_") ->
                "Cuidado. Desvío de ruta. $body."
            kind == "score_alert" -> "Atención. Puntaje de conducción bajo. $body."
            kind == "score_warn" || kind.startsWith("score_") ->
                "Cuidado. Puntaje de conducción. $body."
            kind == "shift_summary" -> "Turno cerrado. $body."
            kind == "shift_fatigue" -> "Atención. Turno prolongado. $body."
            kind == "shift_warn" || kind.startsWith("shift_") ->
                "Cuidado. Duración de turno. $body."
            kind == "cabin_overtemp" -> "Atención. Temperatura de cabina crítica. $body."
            kind == "cabin_warn" || kind.startsWith("cabin_") ->
                "Cuidado. Cabina caliente. $body."
            kind == "coolant_overheat" -> "Atención. Temperatura del motor crítica. $body."
            kind == "coolant_warn" || kind.startsWith("coolant_") ->
                "Cuidado. Motor caliente. $body."
            kind == "rpm_alert" -> "Atención. Régimen del motor crítico. $body."
            kind == "rpm_warn" || kind.startsWith("rpm_") ->
                "Cuidado. Revoluciones altas. $body."
            kind == "tow_alert" -> "Atención. Posible remolque. $body."
            kind == "tow_warn" || kind.startsWith("tow_") ->
                "Cuidado. Movimiento sin ignición. $body."
            kind == "fuel_drop_alert" -> "Atención. Caída brusca de combustible. $body."
            kind == "fuel_drop_warn" || kind.startsWith("fuel_drop") ->
                "Cuidado. Combustible bajando rápido. $body."
            kind == "battery_crit" -> "Atención. Batería crítica. $body."
            kind == "battery_warn" || kind.startsWith("battery_") ->
                "Cuidado. Voltaje de batería bajo. $body."
            kind.startsWith("dtc:") || kind == "dtc" ->
                "Atención. Código de falla. $body."
            kind == "soc_low" -> "Atención. Batería baja. $body."
            kind == "fuel_low" -> "Atención. Combustible bajo. $body."
            kind == "range_low" -> "Atención. Autonomía baja. $body."
            kind == "idle_alert" || kind == "idle_warn" || kind.startsWith("idle_") ->
                "Atención. Motor en ralentí. $body."
            kind == "panic" -> "Emergencia. SOS enviado. $body."
            kind == "incident" -> "Incidente reportado. $body."
            kind == "message_reply" -> "Respuesta enviada. $body."
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
