package com.veplayer.app.fleet

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pending fleet message awaiting driver ack / reply.
 */
object MessageReplyBus {
    data class Pending(
        val alertId: Long,
        val text: String,
        val commandId: Long? = null,
        val requiresAck: Boolean = true,
        val status: String = "pending", // pending | acked | replied
        val replyText: String = "",
        val tsMs: Long = System.currentTimeMillis(),
    )

    val canned =
        listOf(
            "ok" to "OK",
            "recibido" to "Recibido",
            "en_camino" to "En camino",
            "retraso" to "Retraso",
            "ayuda" to "Ayuda",
        )

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    fun offer(
        alertId: Long,
        text: String,
        commandId: Long? = null,
        requiresAck: Boolean = true,
    ) {
        if (alertId <= 0 || text.isBlank()) return
        val cur = _pending.value
        if (cur != null && cur.alertId == alertId && cur.status != "pending") return
        _pending.value =
            Pending(
                alertId = alertId,
                text = text.trim(),
                commandId = commandId,
                requiresAck = requiresAck,
            )
    }

    fun markAcked() {
        val cur = _pending.value ?: return
        _pending.value = cur.copy(status = "acked")
    }

    fun markReplied(reply: String) {
        val cur = _pending.value ?: return
        _pending.value = cur.copy(status = "replied", replyText = reply)
    }

    fun clear() {
        _pending.value = null
    }

    fun label(p: Pending? = null): String {
        val cur = p ?: _pending.value ?: return ""
        return when (cur.status) {
            "acked" -> "Msg ✓ · ${cur.text.take(36)}"
            "replied" -> "Msg → ${cur.replyText.ifBlank { "ok" }.take(24)}"
            else -> "Msg · ${cur.text.take(40)}"
        }
    }
}
