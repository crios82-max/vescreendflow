package com.veplayer.app.vehicle.can

/** Classic CAN / CAN FD payload (data length ≤ 8 for classic). */
data class CanFrame(
    val id: Int,
    val data: ByteArray,
    val extended: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    val hexId: String
        get() = if (extended) "%08X".format(id) else "%03X".format(id)

    fun dataHex(): String = data.joinToString("") { "%02X".format(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CanFrame) return false
        return id == other.id && data.contentEquals(other.data) && extended == other.extended
    }

    override fun hashCode(): Int = 31 * id + data.contentHashCode() + if (extended) 1 else 0

    companion object {
        fun classic(
            id: Int,
            vararg bytes: Int,
        ): CanFrame =
            CanFrame(
                id = id and 0x7FF,
                data = bytes.map { (it and 0xFF).toByte() }.toByteArray(),
                extended = false,
            )
    }
}
