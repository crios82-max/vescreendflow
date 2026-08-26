package com.veplayer.app.vehicle.can

/**
 * Physical / virtual CAN transport.
 * Implementations: USB SLCAN, CarPropertyManager, SocketCAN (JNI), simulator.
 */
interface CanTransport {
    val name: String
    fun open(): Boolean
    fun close()
    fun isOpen(): Boolean
    /** Blocking-ish read with timeout; return null on timeout / empty. */
    fun readFrame(timeoutMs: Long = 200): CanFrame?
    fun writeFrame(frame: CanFrame): Boolean = false
}

enum class CanBackend(val id: String, val label: String) {
    AUTO("auto", "Auto (Car → USB → sim)"),
    CAR("car", "Android Automotive CarProperty"),
    USB("usb", "USB SLCAN / CAN adapter"),
    SOCKET("socket", "SocketCAN (JNI can0)"),
    SIM("sim", "CAN frame simulator"),
    ;

    companion object {
        fun fromId(raw: String?): CanBackend =
            entries.firstOrNull { it.id == raw?.lowercase() } ?: AUTO
    }
}
