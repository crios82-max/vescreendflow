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
        val fuelRateGps: Float? = null,
        val outdoorTempC: Float? = null,
        val throttlePct: Float? = null,
        val engineLoadPct: Float? = null,
        /** Short-term fuel trim % (OBD PID 0106), signed. */
        val fuelTrimStftPct: Float? = null,
        /** Long-term fuel trim % (OBD PID 0107), signed. */
        val fuelTrimLtftPct: Float? = null,
        /** Intake manifold absolute pressure (OBD PID 010B), kPa. */
        val mapKpa: Float? = null,
        /** Catalyst temperature (OBD PID 0134), °C. */
        val catalystTempC: Float? = null,
        /** Mass air flow (OBD PID 0110), grams/sec. */
        val mafGps: Float? = null,
        /** Fuel rail pressure (OBD PID 010A), kPa. */
        val fuelPressureKpa: Float? = null,
        /** Barometric pressure (OBD PID 0133), kPa. */
        val baroKpa: Float? = null,
        /** Ignition timing advance (OBD PID 010E), degrees. */
        val timingAdvanceDeg: Float? = null,
        /** O2 sensor voltage B1S1 (OBD PID 014A), volts. */
        val o2B1s1Volts: Float? = null,
        /** Absolute engine load % (OBD PID 0143). */
        val absoluteLoadPct: Float? = null,
        /** Relative throttle % (OBD PID 0145). */
        val relativeThrottlePct: Float? = null,
        /** Accelerator pedal D % (OBD PID 0149). */
        val accelPedalPct: Float? = null,
        /** O2 sensor voltage B1S2 (OBD PID 014B), volts. */
        val o2B1s2Volts: Float? = null,
        /** EGR error % (OBD PID 014D), signed. */
        val egrErrorPct: Float? = null,
        /** Commanded equivalence ratio (OBD PID 0144). */
        val equivRatio: Float? = null,
        /** Evap purge flow % (OBD PID 014E). */
        val evapPurgePct: Float? = null,
        /** Ethanol fuel % (OBD PID 0152). */
        val ethanolPct: Float? = null,
        /** Evap vapor pressure Pa (OBD PID 0153), signed. */
        val evapVaporPa: Float? = null,
        /** Fuel rail absolute pressure kPa (OBD PID 0159). */
        val fuelRailAbsKpa: Float? = null,
        /** Commanded EGR % (OBD PID 014C). */
        val egrCmdPct: Float? = null,
        /** Relative accelerator pedal % (OBD PID 015A). */
        val relAccelPedalPct: Float? = null,
        /** Driver demand torque % (OBD PID 0161), signed. */
        val driverTorquePct: Float? = null,
        /** Actual engine torque % (OBD PID 0162), signed. */
        val actualTorquePct: Float? = null,
        /** Catalyst temperature bank 2 °C (OBD PID 0170). */
        val catalystB2TempC: Float? = null,
        /** Catalyst temp bank 1 sensor 2 °C (OBD PID 0171). */
        val catalystB1s2TempC: Float? = null,
        /** Catalyst temp bank 2 sensor 2 °C (OBD PID 0172). */
        val catalystB2s2TempC: Float? = null,
        /** Catalyst temp bank 1 sensor 3 °C (OBD PID 0173). */
        val catalystB1s3TempC: Float? = null,
        /** Catalyst temp bank 2 sensor 3 °C (OBD PID 0174). */
        val catalystB2s3TempC: Float? = null,
        /** Catalyst temp bank 1 sensor 4 °C (OBD PID 0175). */
        val catalystB1s4TempC: Float? = null,
        /** Catalyst temp bank 2 sensor 4 °C (OBD PID 0176). */
        val catalystB2s4TempC: Float? = null,
        /** STFT secondary O2 B1 % (OBD PID 0155), signed. */
        val fuelTrimStft2B1Pct: Float? = null,
        /** LTFT secondary O2 B1 % (OBD PID 0156), signed. */
        val fuelTrimLtft2B1Pct: Float? = null,
        /** STFT secondary O2 B2 % (OBD PID 0157), signed. */
        val fuelTrimStft2B2Pct: Float? = null,
        /** LTFT secondary O2 B2 % (OBD PID 0158), signed. */
        val fuelTrimLtft2B2Pct: Float? = null,
        /** Catalyst temp bank 1 sensor 5 °C (OBD PID 0177). */
        val catalystB1s5TempC: Float? = null,
        /** Catalyst temp bank 2 sensor 5 °C (OBD PID 0178). */
        val catalystB2s5TempC: Float? = null,
        /** Fuel injection timing ° (OBD PID 015D), signed. */
        val fuelInjectTimingDeg: Float? = null,
        /** Hybrid pack remaining life % (OBD PID 015B). */
        val hybridBattLifePct: Float? = null,
        /** Engine reference torque Nm (OBD PID 0163). */
        val engineRefTorqueNm: Float? = null,
        /** Catalyst temp bank 1 sensor 6 °C (OBD PID 0179). */
        val catalystB1s6TempC: Float? = null,
        /** Catalyst temp bank 2 sensor 6 °C (OBD PID 017A). */
        val catalystB2s6TempC: Float? = null,
        /** Absolute throttle B % (OBD PID 0147). */
        val throttleBPct: Float? = null,
        /** Absolute throttle C % (OBD PID 0148). */
        val throttleCPct: Float? = null,
        /** Time run with MIL on min (OBD PID 0154). */
        val milTimeMin: Int? = null,
        /** Catalyst temp bank 1 sensor 7 °C (OBD PID 017B). */
        val catalystB1s7TempC: Float? = null,
        /** Catalyst temp bank 2 sensor 7 °C (OBD PID 017C). */
        val catalystB2s7TempC: Float? = null,
        /** Fuel type code (OBD PID 0151). */
        val fuelTypeCode: Int? = null,
        /** Max equivalence ratio (OBD PID 014F). */
        val maxEquivRatio: Float? = null,
        /** Max MAF g/s (OBD PID 0150). */
        val maxMafGps: Float? = null,
        /** Catalyst temp bank 1 sensor 8 °C (OBD PID 017D). */
        val catalystB1s8TempC: Float? = null,
        /** Catalyst temp bank 2 sensor 8 °C (OBD PID 017E). */
        val catalystB2s8TempC: Float? = null,
        /** Max available engine torque % (OBD PID 0164). */
        val maxAvailTorquePct: Float? = null,
        /** MAF sensor intake air °C (OBD PID 0166). */
        val mafSensorIatC: Float? = null,
        /** Auxiliary input status (OBD PID 0165). */
        val auxInputStatus: Int? = null,
        val runtimeSec: Int? = null,
        val milDistanceKm: Float? = null,
        val distSinceClearKm: Float? = null,
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
            0x5E -> {
                if (data.size < 2) PidValues()
                else PidValues(fuelRateGps = ((data[0] * 256) + data[1]) / 20f)
            }
            0x46 -> PidValues(outdoorTempC = (data.getOrNull(0)?.minus(40))?.toFloat())
            0x11 -> PidValues(throttlePct = data.getOrNull(0)?.let { it * 100f / 255f })
            0x04 -> PidValues(engineLoadPct = data.getOrNull(0)?.let { it * 100f / 255f })
            0x06 ->
                PidValues(
                    fuelTrimStftPct =
                        data.getOrNull(0)?.let { (it - 128) * 100f / 128f },
                )
            0x0A -> PidValues(fuelPressureKpa = data.getOrNull(0)?.let { it * 3f })
            0x0E ->
                PidValues(
                    timingAdvanceDeg =
                        data.getOrNull(0)?.let { (it / 2f) - 64f },
                )
            0x07 ->
                PidValues(
                    fuelTrimLtftPct =
                        data.getOrNull(0)?.let { (it - 128) * 100f / 128f },
                )
            0x0B -> PidValues(mapKpa = data.getOrNull(0)?.toFloat())
            0x10 -> {
                if (data.size < 2) PidValues()
                else PidValues(mafGps = ((data[0] * 256) + data[1]) / 100f)
            }
            0x34 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystTempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x33 -> PidValues(baroKpa = data.getOrNull(0)?.toFloat())
            0x4A ->
                PidValues(
                    o2B1s1Volts = data.getOrNull(0)?.let { it / 200f },
                )
            0x43 -> {
                if (data.size < 2) PidValues()
                else PidValues(absoluteLoadPct = ((data[0] * 256) + data[1]) * 100f / 255f)
            }
            0x45 -> PidValues(relativeThrottlePct = data.getOrNull(0)?.let { it * 100f / 255f })
            0x49 -> PidValues(accelPedalPct = data.getOrNull(0)?.let { it * 100f / 255f })
            0x4B ->
                PidValues(
                    o2B1s2Volts = data.getOrNull(0)?.let { it / 200f },
                )
            0x4D ->
                PidValues(
                    egrErrorPct = data.getOrNull(0)?.let { (it - 128) * 100f / 128f },
                )
            0x44 -> {
                if (data.size < 2) PidValues()
                else PidValues(equivRatio = ((data[0] * 256) + data[1]) / 32768f)
            }
            0x4E -> PidValues(evapPurgePct = data.getOrNull(0)?.let { it * 100f / 255f })
            0x52 -> PidValues(ethanolPct = data.getOrNull(0)?.let { it * 100f / 255f })
            0x53 -> {
                if (data.size < 2) PidValues()
                else {
                    val raw = (data[0] shl 8) or data[1]
                    val signed = if (raw and 0x8000 != 0) raw - 0x10000 else raw
                    PidValues(evapVaporPa = signed / 4f)
                }
            }
            0x59 -> {
                if (data.size < 2) PidValues()
                else PidValues(fuelRailAbsKpa = ((data[0] * 256) + data[1]) * 10f)
            }
            0x4C -> PidValues(egrCmdPct = data.getOrNull(0)?.let { it * 100f / 255f })
            0x5A -> PidValues(relAccelPedalPct = data.getOrNull(0)?.let { it * 100f / 255f })
            0x61 ->
                PidValues(
                    driverTorquePct = data.getOrNull(0)?.let { (it - 125).toFloat() },
                )
            0x62 ->
                PidValues(
                    actualTorquePct = data.getOrNull(0)?.let { (it - 125).toFloat() },
                )
            0x70 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB2TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x71 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB1s2TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x72 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB2s2TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x73 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB1s3TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x74 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB2s3TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x75 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB1s4TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x76 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB2s4TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x55 ->
                PidValues(
                    fuelTrimStft2B1Pct = data.getOrNull(0)?.let { (it - 128) * 100f / 128f },
                )
            0x56 ->
                PidValues(
                    fuelTrimLtft2B1Pct = data.getOrNull(0)?.let { (it - 128) * 100f / 128f },
                )
            0x57 ->
                PidValues(
                    fuelTrimStft2B2Pct = data.getOrNull(0)?.let { (it - 128) * 100f / 128f },
                )
            0x58 ->
                PidValues(
                    fuelTrimLtft2B2Pct = data.getOrNull(0)?.let { (it - 128) * 100f / 128f },
                )
            0x77 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB1s5TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x78 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB2s5TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x5D -> {
                if (data.size < 2) PidValues()
                else {
                    val raw = (data[0] shl 8) or data[1]
                    val signed = if (raw and 0x8000 != 0) raw - 0x10000 else raw
                    PidValues(fuelInjectTimingDeg = signed / 128f)
                }
            }
            0x5B -> PidValues(hybridBattLifePct = data.getOrNull(0)?.let { it * 100f / 255f })
            0x63 -> {
                if (data.size < 2) PidValues()
                else PidValues(engineRefTorqueNm = ((data[0] * 256) + data[1]).toFloat())
            }
            0x79 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB1s6TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x7A -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB2s6TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x47 -> PidValues(throttleBPct = data.getOrNull(0)?.let { it * 100f / 255f })
            0x48 -> PidValues(throttleCPct = data.getOrNull(0)?.let { it * 100f / 255f })
            0x54 -> {
                if (data.size < 2) PidValues()
                else PidValues(milTimeMin = (data[0] * 256) + data[1])
            }
            0x7B -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB1s7TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x7C -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB2s7TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x51 -> PidValues(fuelTypeCode = data.getOrNull(0))
            0x4F -> {
                if (data.size < 2) PidValues()
                else PidValues(maxEquivRatio = ((data[0] * 256) + data[1]) / 32768f)
            }
            0x50 -> {
                if (data.size < 2) PidValues()
                else PidValues(maxMafGps = ((data[0] * 256) + data[1]) / 100f)
            }
            0x7D -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB1s8TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x7E -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB2s8TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x64 -> PidValues(maxAvailTorquePct = data.getOrNull(0)?.let { (it - 125).toFloat() })
            0x66 -> PidValues(mafSensorIatC = (data.getOrNull(0)?.minus(40))?.toFloat())
            0x65 -> PidValues(auxInputStatus = data.getOrNull(0))
            0x1F -> {
                if (data.size < 2) PidValues()
                else PidValues(runtimeSec = (data[0] * 256) + data[1])
            }
            0x21 -> {
                if (data.size < 2) PidValues()
                else PidValues(milDistanceKm = ((data[0] * 256) + data[1]).toFloat())
            }
            0x31 -> {
                if (data.size < 2) PidValues()
                else PidValues(distSinceClearKm = ((data[0] * 256) + data[1]).toFloat())
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
            fuelRateGps = add.fuelRateGps ?: base.fuelRateGps,
            outdoorTempC = add.outdoorTempC ?: base.outdoorTempC,
            throttlePct = add.throttlePct ?: base.throttlePct,
            engineLoadPct = add.engineLoadPct ?: base.engineLoadPct,
            fuelTrimStftPct = add.fuelTrimStftPct ?: base.fuelTrimStftPct,
            fuelTrimLtftPct = add.fuelTrimLtftPct ?: base.fuelTrimLtftPct,
            mapKpa = add.mapKpa ?: base.mapKpa,
            catalystTempC = add.catalystTempC ?: base.catalystTempC,
            mafGps = add.mafGps ?: base.mafGps,
            fuelPressureKpa = add.fuelPressureKpa ?: base.fuelPressureKpa,
            baroKpa = add.baroKpa ?: base.baroKpa,
            timingAdvanceDeg = add.timingAdvanceDeg ?: base.timingAdvanceDeg,
            o2B1s1Volts = add.o2B1s1Volts ?: base.o2B1s1Volts,
            absoluteLoadPct = add.absoluteLoadPct ?: base.absoluteLoadPct,
            relativeThrottlePct = add.relativeThrottlePct ?: base.relativeThrottlePct,
            accelPedalPct = add.accelPedalPct ?: base.accelPedalPct,
            o2B1s2Volts = add.o2B1s2Volts ?: base.o2B1s2Volts,
            egrErrorPct = add.egrErrorPct ?: base.egrErrorPct,
            equivRatio = add.equivRatio ?: base.equivRatio,
            evapPurgePct = add.evapPurgePct ?: base.evapPurgePct,
            ethanolPct = add.ethanolPct ?: base.ethanolPct,
            evapVaporPa = add.evapVaporPa ?: base.evapVaporPa,
            fuelRailAbsKpa = add.fuelRailAbsKpa ?: base.fuelRailAbsKpa,
            egrCmdPct = add.egrCmdPct ?: base.egrCmdPct,
            relAccelPedalPct = add.relAccelPedalPct ?: base.relAccelPedalPct,
            driverTorquePct = add.driverTorquePct ?: base.driverTorquePct,
            actualTorquePct = add.actualTorquePct ?: base.actualTorquePct,
            catalystB2TempC = add.catalystB2TempC ?: base.catalystB2TempC,
            catalystB1s2TempC = add.catalystB1s2TempC ?: base.catalystB1s2TempC,
            catalystB2s2TempC = add.catalystB2s2TempC ?: base.catalystB2s2TempC,
            catalystB1s3TempC = add.catalystB1s3TempC ?: base.catalystB1s3TempC,
            catalystB2s3TempC = add.catalystB2s3TempC ?: base.catalystB2s3TempC,
            catalystB1s4TempC = add.catalystB1s4TempC ?: base.catalystB1s4TempC,
            catalystB2s4TempC = add.catalystB2s4TempC ?: base.catalystB2s4TempC,
            fuelTrimStft2B1Pct = add.fuelTrimStft2B1Pct ?: base.fuelTrimStft2B1Pct,
            fuelTrimLtft2B1Pct = add.fuelTrimLtft2B1Pct ?: base.fuelTrimLtft2B1Pct,
            fuelTrimStft2B2Pct = add.fuelTrimStft2B2Pct ?: base.fuelTrimStft2B2Pct,
            fuelTrimLtft2B2Pct = add.fuelTrimLtft2B2Pct ?: base.fuelTrimLtft2B2Pct,
            catalystB1s5TempC = add.catalystB1s5TempC ?: base.catalystB1s5TempC,
            catalystB2s5TempC = add.catalystB2s5TempC ?: base.catalystB2s5TempC,
            fuelInjectTimingDeg = add.fuelInjectTimingDeg ?: base.fuelInjectTimingDeg,
            hybridBattLifePct = add.hybridBattLifePct ?: base.hybridBattLifePct,
            engineRefTorqueNm = add.engineRefTorqueNm ?: base.engineRefTorqueNm,
            catalystB1s6TempC = add.catalystB1s6TempC ?: base.catalystB1s6TempC,
            catalystB2s6TempC = add.catalystB2s6TempC ?: base.catalystB2s6TempC,
            throttleBPct = add.throttleBPct ?: base.throttleBPct,
            throttleCPct = add.throttleCPct ?: base.throttleCPct,
            milTimeMin = add.milTimeMin ?: base.milTimeMin,
            catalystB1s7TempC = add.catalystB1s7TempC ?: base.catalystB1s7TempC,
            catalystB2s7TempC = add.catalystB2s7TempC ?: base.catalystB2s7TempC,
            fuelTypeCode = add.fuelTypeCode ?: base.fuelTypeCode,
            maxEquivRatio = add.maxEquivRatio ?: base.maxEquivRatio,
            maxMafGps = add.maxMafGps ?: base.maxMafGps,
            catalystB1s8TempC = add.catalystB1s8TempC ?: base.catalystB1s8TempC,
            catalystB2s8TempC = add.catalystB2s8TempC ?: base.catalystB2s8TempC,
            maxAvailTorquePct = add.maxAvailTorquePct ?: base.maxAvailTorquePct,
            mafSensorIatC = add.mafSensorIatC ?: base.mafSensorIatC,
            auxInputStatus = add.auxInputStatus ?: base.auxInputStatus,
            runtimeSec = add.runtimeSec ?: base.runtimeSec,
            milDistanceKm = add.milDistanceKm ?: base.milDistanceKm,
            distSinceClearKm = add.distSinceClearKm ?: base.distSinceClearKm,
            batteryVoltageV = add.batteryVoltageV ?: base.batteryVoltageV,
        )
}
