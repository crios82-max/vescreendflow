package com.veplayer.app.vehicle

/**
 * Decode ELM327 / ISO 15765 Mode 01 responses.
 * Accepts noisy adapter text ("SEARCHING…", spaces, multi-line).
 */
object ObdPidParser {
    data class PidValues(
        val speedKmh: Float? = null,
        val rpm: Float? = null,
        val coolantC: Float? = null,
        val oilTempC: Float? = null,
        val intakeAirC: Float? = null,
        val fuelPct: Float? = null,
        val outdoorTempC: Float? = null,
        val throttlePct: Float? = null,
        val engineLoadPct: Float? = null,
        val runtimeSec: Int? = null,
        val batteryVoltageV: Float? = null,
    )

    fun extractPayloadBytes(raw: String): List<Int>? {
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

        // Prefer "41 XX …" mode-01 positive response
        val tokens = cleaned.split(' ').filter { it.matches(Regex("[0-9A-F]{2}")) }
        if (tokens.isEmpty()) return null
        val ints = tokens.mapNotNull { it.toIntOrNull(16) }
        val idx = ints.indexOfFirst { it == 0x41 }
        if (idx < 0 || idx + 1 >= ints.size) return null
        return ints.drop(idx)
    }

    /** Parse a single Mode 01 response into known fields. */
    fun parseMode01(raw: String): PidValues {
        val bytes = extractPayloadBytes(raw) ?: return PidValues()
        if (bytes.size < 3 || bytes[0] != 0x41) return PidValues()
        val pid = bytes[1]
        val data = bytes.drop(2)
        return when (pid) {
            0x0D -> PidValues(speedKmh = data.getOrNull(0)?.toFloat())
            0x0C -> {
                if (data.size < 2) PidValues()
                else PidValues(rpm = ((data[0] * 256) + data[1]) / 4f)
            }
            0x05 -> PidValues(coolantC = (data.getOrNull(0)?.minus(40))?.toFloat())
            0x5C -> PidValues(oilTempC = (data.getOrNull(0)?.minus(40))?.toFloat())
            0x0F -> PidValues(intakeAirC = (data.getOrNull(0)?.minus(40))?.toFloat())
            0x2F -> PidValues(fuelPct = data.getOrNull(0)?.let { it * 100f / 255f })
            0x46 -> PidValues(outdoorTempC = (data.getOrNull(0)?.minus(40))?.toFloat())
            0x11 -> PidValues(throttlePct = data.getOrNull(0)?.let { it * 100f / 255f })
            0x04 -> PidValues(engineLoadPct = data.getOrNull(0)?.let { it * 100f / 255f })
            0x1F -> {
                if (data.size < 2) PidValues()
                else PidValues(runtimeSec = (data[0] * 256) + data[1])
            }
            0x42 -> {
                if (data.size < 2) PidValues()
                else PidValues(batteryVoltageV = ((data[0] * 256) + data[1]) / 1000f)
            }
            else -> PidValues()
        }
    }

    fun merge(base: PidValues, add: PidValues): PidValues =
        PidValues(
            speedKmh = add.speedKmh ?: base.speedKmh,
            rpm = add.rpm ?: base.rpm,
            coolantC = add.coolantC ?: base.coolantC,
            oilTempC = add.oilTempC ?: base.oilTempC,
            intakeAirC = add.intakeAirC ?: base.intakeAirC,
            fuelPct = add.fuelPct ?: base.fuelPct,
            outdoorTempC = add.outdoorTempC ?: base.outdoorTempC,
            throttlePct = add.throttlePct ?: base.throttlePct,
            engineLoadPct = add.engineLoadPct ?: base.engineLoadPct,
            runtimeSec = add.runtimeSec ?: base.runtimeSec,
            batteryVoltageV = add.batteryVoltageV ?: base.batteryVoltageV,
        )
}
