package com.veplayer.app.vehicle

/**
 * OBD-II diagnostic trouble codes (Modes 03 / 07 / 0A) + MIL (PID 0101).
 */
object ObdDtc {
    data class Code(
        val code: String,
        val status: String, // stored | pending | permanent
    ) {
        fun toJsonMap(): Map<String, Any?> = mapOf("code" to code, "status" to status)
    }

    data class Snapshot(
        val mil: Boolean = false,
        val dtcCount: Int = 0,
        val codes: List<Code> = emptyList(),
    ) {
        fun toJsonList(): List<Map<String, Any?>> = codes.map { it.toJsonMap() }
    }

    /** Decode two DTC bytes → e.g. P0133, C1234. */
    fun decodePair(hi: Int, lo: Int): String {
        val type =
            when ((hi shr 6) and 0x3) {
                0 -> "P"
                1 -> "C"
                2 -> "B"
                else -> "U"
            }
        val d1 = (hi shr 4) and 0x3
        val d2 = hi and 0xF
        val d3 = (lo shr 4) and 0xF
        val d4 = lo and 0xF
        return "$type$d1${d2.toString(16).uppercase()}${d3.toString(16).uppercase()}${d4.toString(16).uppercase()}"
    }

    fun statusForMode(modeEcho: Int): String =
        when (modeEcho) {
            0x43 -> "stored"
            0x47 -> "pending"
            0x4A -> "permanent"
            else -> "stored"
        }

    /**
     * Extract hex payload starting at Mode response echo (41/43/47/4A).
     */
    fun extractModeBytes(raw: String, vararg modes: Int): List<Int>? {
        val cleaned =
            raw.uppercase()
                .replace("SEARCHING...", "")
                .replace("SEARCHING…", "")
                .replace("STOPPED", "")
                .replace("NO DATA", "")
                .replace("UNABLE TO CONNECT", "")
                .replace("BUS INIT", "")
                .replace("OK", "")
                .replace(">", "")
                .replace("\r", " ")
                .replace("\n", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        if (cleaned.isBlank() || cleaned.contains("ERROR") || cleaned == "?") return null
        val tokens = cleaned.split(' ').filter { it.matches(Regex("[0-9A-F]{2}")) }
        if (tokens.isEmpty()) return null
        val ints = tokens.mapNotNull { it.toIntOrNull(16) }
        val want = modes.toSet()
        val idx = ints.indexOfFirst { it in want }
        if (idx < 0) return null
        return ints.drop(idx)
    }

    /** PID 0101 → MIL + reported DTC count. */
    fun parseMonitorStatus(raw: String): Pair<Boolean, Int>? {
        val bytes = extractModeBytes(raw, 0x41) ?: return null
        if (bytes.size < 3 || bytes[0] != 0x41 || bytes[1] != 0x01) return null
        val a = bytes.getOrNull(2) ?: return null
        val mil = (a and 0x80) != 0
        val count = a and 0x7F
        return mil to count
    }

    /** Modes 03/07/0A positive responses (43/47/4A). */
    fun parseDtcResponse(raw: String, expectedMode: Int): List<Code> {
        val echo =
            when (expectedMode) {
                0x03 -> 0x43
                0x07 -> 0x47
                0x0A -> 0x4A
                else -> return emptyList()
            }
        val bytes = extractModeBytes(raw, echo) ?: return emptyList()
        if (bytes.isEmpty() || bytes[0] != echo) return emptyList()
        val status = statusForMode(echo)
        val out = mutableListOf<Code>()
        // ELM327 typically: 43 <hi lo>… with 00 00 padding (no count byte).
        // ISO may insert a count nibble/byte; only skip when it clearly matches.
        var i = 1
        val rest = bytes.size - 1
        if (rest >= 1) {
            val maybeCount = bytes[1]
            if (maybeCount in 1..0x10 && rest == 1 + maybeCount * 2) {
                i = 2
            }
        }
        while (i + 1 < bytes.size) {
            val hi = bytes[i]
            val lo = bytes[i + 1]
            i += 2
            if (hi == 0 && lo == 0) continue
            out += Code(code = decodePair(hi, lo), status = status)
        }
        return out
    }

    fun demoSeed(): Snapshot =
        Snapshot(
            mil = true,
            dtcCount = 2,
            codes =
                listOf(
                    Code("P0420", "stored"),
                    Code("P0301", "pending"),
                ),
        )
}
