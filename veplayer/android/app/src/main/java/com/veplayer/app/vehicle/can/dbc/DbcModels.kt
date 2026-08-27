package com.veplayer.app.vehicle.can.dbc

/** One DBC signal (SG_ line). */
data class DbcSignal(
    val name: String,
    val startBit: Int,
    val length: Int,
    /** true = Intel/little-endian (@1), false = Motorola/big-endian (@0). */
    val littleEndian: Boolean,
    val signed: Boolean,
    val factor: Double,
    val offset: Double,
    val min: Double,
    val max: Double,
    val unit: String,
)

data class DbcMessage(
    val id: Int,
    val name: String,
    val dlc: Int,
    val signals: List<DbcSignal>,
)

data class DbcDatabase(
    val messages: Map<Int, DbcMessage>,
    val sourceLabel: String,
) {
    val messageCount: Int get() = messages.size
    val signalCount: Int get() = messages.values.sumOf { it.signals.size }

    fun message(id: Int): DbcMessage? = messages[id and 0x7FF] ?: messages[id]
}
