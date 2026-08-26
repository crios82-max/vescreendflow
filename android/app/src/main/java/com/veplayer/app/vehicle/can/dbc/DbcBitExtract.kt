package com.veplayer.app.vehicle.can.dbc

/**
 * Extract physical signal value from classic CAN payload using DBC bit layout.
 */
object DbcBitExtract {
    fun physical(
        data: ByteArray,
        signal: DbcSignal,
    ): Double {
        val raw = rawUnsigned(data, signal)
        val signedRaw =
            if (signal.signed) {
                val signBit = 1L shl (signal.length - 1)
                if (raw and signBit != 0L) raw - (1L shl signal.length) else raw
            } else {
                raw
            }
        return signedRaw * signal.factor + signal.offset
    }

    fun rawUnsigned(
        data: ByteArray,
        signal: DbcSignal,
    ): Long {
        if (signal.length <= 0 || signal.length > 64) return 0L
        return if (signal.littleEndian) {
            extractIntel(data, signal.startBit, signal.length)
        } else {
            extractMotorola(data, signal.startBit, signal.length)
        }
    }

    /** Intel: startBit is LSB position in the message bit stream (bit 0 = byte0 bit0). */
    private fun extractIntel(
        data: ByteArray,
        startBit: Int,
        length: Int,
    ): Long {
        var value = 0L
        for (i in 0 until length) {
            val bit = startBit + i
            val byteIndex = bit / 8
            val bitInByte = bit % 8
            if (byteIndex !in data.indices) continue
            val b = data[byteIndex].toInt() and 0xFF
            if ((b shr bitInByte) and 1 == 1) {
                value = value or (1L shl i)
            }
        }
        return value
    }

    /**
     * Motorola (big-endian) as used by Vector: startBit is MSB position,
     * bits counted within each byte MSB-first, bytes ascending.
     */
    private fun extractMotorola(
        data: ByteArray,
        startBit: Int,
        length: Int,
    ): Long {
        var value = 0L
        var bit = startBit
        for (i in 0 until length) {
            val byteIndex = bit / 8
            val bitInByte = 7 - (bit % 8)
            if (byteIndex in data.indices) {
                val b = data[byteIndex].toInt() and 0xFF
                if ((b shr bitInByte) and 1 == 1) {
                    value = value or (1L shl (length - 1 - i))
                }
            }
            // next bit toward LSB within motorola layout
            if (bit % 8 == 0) {
                bit += 15
            } else {
                bit -= 1
            }
        }
        return value
    }
}
