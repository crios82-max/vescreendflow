package com.veplayer.app.vehicle.can.dbc

/**
 * Minimal DBC text parser (BO_ / SG_). Enough for fleet OEM files and VePlayer demo.
 * Ignores CM_, BA_, VAL_, etc.
 */
object DbcParser {
    private val bo =
        Regex(
            """^BO_\s+(\d+)\s+(\w+)\s*:\s*(\d+)\s+(\w+)""",
        )
    private val sg =
        Regex(
            """^\s*SG_\s+(\w+)\s*(?:M|m\d+)?\s*:\s*(\d+)\|(\d+)@([01])([+-])\s*\(\s*([^,]+)\s*,\s*([^)]+)\s*\)\s*\[\s*([^|]*)\s*\|\s*([^\]]*)\s*\]\s*"([^"]*)""",
        )

    fun parse(
        text: String,
        sourceLabel: String = "dbc",
    ): DbcDatabase {
        val messages = linkedMapOf<Int, DbcMessage>()
        var currentId: Int? = null
        var currentName = ""
        var currentDlc = 8
        val currentSignals = mutableListOf<DbcSignal>()

        fun flush() {
            val id = currentId ?: return
            messages[id] =
                DbcMessage(
                    id = id,
                    name = currentName,
                    dlc = currentDlc,
                    signals = currentSignals.toList(),
                )
            currentSignals.clear()
            currentId = null
        }

        for (raw in text.lineSequence()) {
            val line = raw.trimEnd()
            if (line.isBlank() || line.startsWith("//") || line.startsWith("VERSION")) continue
            val boMatch = bo.find(line)
            if (boMatch != null) {
                flush()
                currentId = boMatch.groupValues[1].toInt()
                currentName = boMatch.groupValues[2]
                currentDlc = boMatch.groupValues[3].toIntOrNull() ?: 8
                continue
            }
            val sgMatch = sg.find(line)
            if (sgMatch != null && currentId != null) {
                currentSignals +=
                    DbcSignal(
                        name = sgMatch.groupValues[1],
                        startBit = sgMatch.groupValues[2].toInt(),
                        length = sgMatch.groupValues[3].toInt(),
                        littleEndian = sgMatch.groupValues[4] == "1",
                        signed = sgMatch.groupValues[5] == "-",
                        factor = sgMatch.groupValues[6].trim().toDoubleOrNull() ?: 1.0,
                        offset = sgMatch.groupValues[7].trim().toDoubleOrNull() ?: 0.0,
                        min = sgMatch.groupValues[8].trim().toDoubleOrNull() ?: 0.0,
                        max = sgMatch.groupValues[9].trim().toDoubleOrNull() ?: 0.0,
                        unit = sgMatch.groupValues[10],
                    )
            }
        }
        flush()
        return DbcDatabase(messages = messages, sourceLabel = sourceLabel)
    }
}
