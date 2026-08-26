package com.veplayer.app.vehicle

import org.json.JSONArray
import org.json.JSONObject

/**
 * Odometer-based maintenance intervals.
 * Shared with `veplayer/scripts/maintenance-smoke.mjs`.
 */
object Maintenance {
    data class Item(
        val kind: String,
        val label: String,
        val intervalKm: Float,
        val lastServiceOdoKm: Float,
        val warnKm: Float = 500f,
        val enabled: Boolean = true,
    )

    data class Status(
        val item: Item,
        val odoKm: Float?,
        val dueAtKm: Float,
        val remainingKm: Float?,
        /** ok | warn | due | off */
        val band: String,
    )

    fun defaults(lastOdoKm: Float = 0f): List<Item> =
        listOf(
            Item("oil", "Aceite", 5000f, lastOdoKm, 500f),
            Item("tires", "Neumáticos", 10000f, lastOdoKm, 800f),
            Item("inspection", "Revisión", 15000f, lastOdoKm, 1000f),
            Item("brakes", "Frenos", 20000f, lastOdoKm, 1000f),
            Item("filter", "Filtro aire", 15000f, lastOdoKm, 500f),
        )

    fun evaluate(
        item: Item,
        odoKm: Float?,
    ): Status {
        val dueAt = item.lastServiceOdoKm + item.intervalKm
        if (!item.enabled) {
            return Status(
                item = item,
                odoKm = odoKm,
                dueAtKm = dueAt,
                remainingKm = odoKm?.let { dueAt - it },
                band = "off",
            )
        }
        if (odoKm == null) {
            return Status(item, null, dueAt, null, "ok")
        }
        val remaining = dueAt - odoKm
        val band =
            when {
                remaining <= 0f -> "due"
                remaining <= item.warnKm -> "warn"
                else -> "ok"
            }
        return Status(item, odoKm, dueAt, remaining, band)
    }

    fun evaluateAll(
        items: List<Item>,
        odoKm: Float?,
    ): List<Status> = items.map { evaluate(it, odoKm) }

    fun voicePhrase(st: Status): String {
        val label = st.item.label
        return when (st.band) {
            "due" -> {
                val over = st.remainingKm?.let { kotlin.math.abs(it).toInt() } ?: 0
                "Mantenimiento vencido: $label. $over kilómetros de atraso."
            }
            "warn" -> {
                val rem = st.remainingKm?.toInt() ?: 0
                "Próximo servicio: $label en $rem kilómetros."
            }
            else -> "Servicio $label al día."
        }
    }

    fun parseJson(raw: String): List<Item> {
        if (raw.isBlank()) return defaults()
        return runCatching {
            val arr = JSONArray(raw)
            if (arr.length() == 0) return defaults()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Item(
                            kind = o.optString("kind").ifBlank { "custom$i" },
                            label = o.optString("label").ifBlank { o.optString("kind") },
                            intervalKm = o.optDouble("interval_km", 10000.0).toFloat(),
                            lastServiceOdoKm = o.optDouble("last_odo_km", 0.0).toFloat(),
                            warnKm = o.optDouble("warn_km", 500.0).toFloat(),
                            enabled = o.optBoolean("enabled", true),
                        ),
                    )
                }
            }
        }.getOrElse { defaults() }
    }

    fun toJson(items: List<Item>): String {
        val arr = JSONArray()
        for (it in items) {
            arr.put(
                JSONObject()
                    .put("kind", it.kind)
                    .put("label", it.label)
                    .put("interval_km", it.intervalKm.toDouble())
                    .put("last_odo_km", it.lastServiceOdoKm.toDouble())
                    .put("warn_km", it.warnKm.toDouble())
                    .put("enabled", it.enabled),
            )
        }
        return arr.toString()
    }

    fun recordService(
        items: List<Item>,
        kind: String,
        odoKm: Float,
    ): List<Item> {
        val k = kind.trim().lowercase()
        return items.map {
            if (it.kind == k) it.copy(lastServiceOdoKm = odoKm) else it
        }
    }
}
