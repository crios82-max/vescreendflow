package com.veplayer.app.vehicle

/** How VePlayer obtains vehicle speed / gear / CAN variables. */
enum class SignalSourceKind(val id: String, val label: String) {
    GPS("gps", "GPS (velocidad)"),
    MOCK("mock", "Mock CAN (demo)"),
    CAN("can", "CAN bus (USB/SocketCAN)"),
    OBD("obd", "OBD-II ELM327"),
    ;

    companion object {
        fun fromId(raw: String?): SignalSourceKind =
            entries.firstOrNull { it.id == raw?.lowercase() } ?: GPS
    }
}
