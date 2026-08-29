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
        /** Catalyst temp bank 1 sensor 9 °C (OBD PID 017F). */
        val catalystB1s9TempC: Float? = null,
        /** Catalyst temp bank 2 sensor 9 °C (OBD PID 0180). */
        val catalystB2s9TempC: Float? = null,
        /** Engine coolant sensor 2 °C (OBD PID 0167). */
        val coolantEct2C: Float? = null,
        /** Intake air sensor 2 °C (OBD PID 0168). */
        val iatSensor2C: Float? = null,
        /** Turbo inlet pressure kPa (OBD PID 016F). */
        val turboInletKpa: Float? = null,
        /** Catalyst temp bank 1 sensor 10 °C (OBD PID 0181). */
        val catalystB1s10TempC: Float? = null,
        /** Catalyst temp bank 2 sensor 10 °C (OBD PID 0182). */
        val catalystB2s10TempC: Float? = null,
        /** EGR temperature °C (OBD PID 016B). */
        val egrTempC: Float? = null,
        /** Diesel intake air flow commanded % (OBD PID 016A). */
        val dieselIafCmdPct: Float? = null,
        /** Commanded throttle actuator % (OBD PID 016C). */
        val thrActuatorPct: Float? = null,
        /** Catalyst temp bank 1 sensor 11 °C (OBD PID 0183). */
        val catalystB1s11TempC: Float? = null,
        /** Catalyst temp bank 2 sensor 11 °C (OBD PID 0184). */
        val catalystB2s11TempC: Float? = null,
        /** Actual EGR % (OBD PID 0169). */
        val actualEgrPct: Float? = null,
        /** Injection pressure control kPa (OBD PID 016E). */
        val injectCtrlKpa: Float? = null,
        /** Fuel pressure control kPa (OBD PID 016D). */
        val fuelCtrlKpa: Float? = null,
        /** Catalyst temp bank 1 sensor 12 °C (OBD PID 0185). */
        val catalystB1s12TempC: Float? = null,
        /** Catalyst temp bank 2 sensor 12 °C (OBD PID 0186). */
        val catalystB2s12TempC: Float? = null,
        /** STFT bank 2 % (OBD PID 0108), signed. */
        val fuelTrimStftB2Pct: Float? = null,
        /** LTFT bank 2 % (OBD PID 0109), signed. */
        val fuelTrimLtftB2Pct: Float? = null,
        /** Catalyst temp bank 1 sensor 13 °C (OBD PID 0187). */
        val catalystB1s13TempC: Float? = null,
        /** Catalyst temp bank 2 sensor 13 °C (OBD PID 0188). */
        val catalystB2s13TempC: Float? = null,
        /** DPF regen trigger % (OBD PID 018B byte C). */
        val dpfTriggerPct: Float? = null,
        /** Absolute throttle G % (OBD PID 018D). */
        val throttleGPct: Float? = null,
        /** Engine friction torque % (OBD PID 018E), signed. */
        val engineFrictionPct: Float? = null,
        /** Catalyst temp bank 1 sensor 14 °C (OBD PID 0189). */
        val catalystB1s14TempC: Float? = null,
        /** Catalyst temp bank 2 sensor 14 °C (OBD PID 018A). */
        val catalystB2s14TempC: Float? = null,
        /** O2 wide-range lambda B1S1 (OBD PID 018C). */
        val o2LambdaB1: Float? = null,
        /** PM sensor normalized B1S1 % (OBD PID 018F bytes C/D). */
        val pmSensorB1Pct: Float? = null,
        /** PM sensor normalized B2S1 % (OBD PID 018F bytes F/G). */
        val pmSensorB2Pct: Float? = null,
        /** EGT bank 1 sensor 5 °C (OBD PID 0198 bytes B/C). */
        val egtB1s5TempC: Float? = null,
        /** EGT bank 2 sensor 5 °C (OBD PID 0199 bytes B/C). */
        val egtB2s5TempC: Float? = null,
        /** O2 lambda bank 1 sensor 3 (OBD PID 019C bytes J/K). */
        val o2LambdaB1s3: Float? = null,
        /** O2 lambda bank 2 sensor 3 (OBD PID 019C bytes N/O). */
        val o2LambdaB2s3: Float? = null,
        /** NOx reagent quality counter hours (OBD PID 0194 bytes C/D). */
        val noxReagentQualHours: Float? = null,
        /** NOx warning system active (OBD PID 0194 byte B bit0). */
        val noxWarningActive: Int? = null,
        /** NOx level-one inducement status (OBD PID 0194 byte B bits 2–1). */
        val noxInduceLevel1: Int? = null,
        /** NOx level-two inducement status (OBD PID 0194 byte B bits 4–3). */
        val noxInduceLevel2: Int? = null,
        /** NOx EGR valve counter hours (OBD PID 0194 bytes I/J). */
        val noxEgrValveCounterHours: Float? = null,
        /** NOx monitor malfunction counter hours (OBD PID 0194 bytes K/L). */
        val noxMonitorMalfunctionHours: Float? = null,
        /** EGT bank 1 sensor 6 °C (OBD PID 0198 bytes D/E). */
        val egtB1s6TempC: Float? = null,
        /** EGT bank 2 sensor 6 °C (OBD PID 0199 bytes D/E). */
        val egtB2s6TempC: Float? = null,
        /** EGT bank 1 sensor 7 °C (OBD PID 0198 bytes F/G). */
        val egtB1s7TempC: Float? = null,
        /** EGT bank 2 sensor 7 °C (OBD PID 0199 bytes F/G). */
        val egtB2s7TempC: Float? = null,
        /** EGT bank 1 sensor 8 °C (OBD PID 0198 bytes H/I). */
        val egtB1s8TempC: Float? = null,
        /** EGT bank 2 sensor 8 °C (OBD PID 0199 bytes H/I). */
        val egtB2s8TempC: Float? = null,
        /** O2 lambda bank 1 sensor 4 (OBD PID 019C bytes L/M). */
        val o2LambdaB1s4: Float? = null,
        /** O2 lambda bank 2 sensor 4 (OBD PID 019C bytes P/Q). */
        val o2LambdaB2s4: Float? = null,
        /** O2 concentration bank 1 sensor 3 % (OBD PID 019C bytes B/C). */
        val o2ConcB1s3Pct: Float? = null,
        /** O2 concentration bank 1 sensor 4 % (OBD PID 019C bytes D/E). */
        val o2ConcB1s4Pct: Float? = null,
        /** O2 concentration bank 2 sensor 3 % (OBD PID 019C bytes F/G). */
        val o2ConcB2s3Pct: Float? = null,
        /** O2 concentration bank 2 sensor 4 % (OBD PID 019C bytes H/I). */
        val o2ConcB2s4Pct: Float? = null,
        /** Commanded DEF dosing % (OBD PID 01A5 byte B / 2). */
        val defDosingCmdPct: Float? = null,
        /** NOx corrected bank 1 sensor 1 ppm (OBD PID 01A1 bytes B/C). */
        val noxCorrectedB1s1Ppm: Float? = null,
        /** NOx corrected bank 1 sensor 2 ppm (OBD PID 01A1 bytes D/E). */
        val noxCorrectedB1s2Ppm: Float? = null,
        /** NOx corrected bank 2 sensor 1 ppm (OBD PID 01A1 bytes F/G). */
        val noxCorrectedB2s1Ppm: Float? = null,
        /** NOx corrected bank 2 sensor 2 ppm (OBD PID 01A1 bytes H/I). */
        val noxCorrectedB2s2Ppm: Float? = null,
        /** NOx concentration sensor 3 ppm (OBD PID 01A7 bytes A/B). */
        val noxConcS3Ppm: Float? = null,
        /** NOx concentration sensor 4 ppm (OBD PID 01A7 bytes C/D). */
        val noxConcS4Ppm: Float? = null,
        /** NOx corrected sensor 3 ppm (OBD PID 01A8 bytes A/B). */
        val noxCorrectedS3Ppm: Float? = null,
        /** NOx corrected sensor 4 ppm (OBD PID 01A8 bytes C/D). */
        val noxCorrectedS4Ppm: Float? = null,
        /** Cylinder fuel rate mg/stroke (OBD PID 01A2). */
        val cylinderFuelRateMg: Float? = null,
        /** Evap system vapor pressure Pa (OBD PID 01A3 bytes B/C). */
        val evapSysVaporPa: Float? = null,
        /** Transmission actual gear ratio (OBD PID 01A4 bytes C/D). */
        val transGearRatio: Float? = null,
        /** OBD odometer km (OBD PID 01A6). */
        val obdOdometerKm: Float? = null,
        /** ABS disable supported flag (OBD PID 01A9 byte A bit0). */
        val absDisableSupported: Int? = null,
        /** ABS disable active flag (OBD PID 01A9 byte B bit0). */
        val absDisabled: Int? = null,
        /** Fuel pressure A kPa (OBD PID 01C5 bytes A/B). */
        val fuelPressAKpa: Float? = null,
        /** Fuel pressure B kPa (OBD PID 01C5 bytes C/D). */
        val fuelPressBKpa: Float? = null,
        /** Distance since reflash km (OBD PID 01C7). */
        val reflashDistKm: Float? = null,
        /** Fuel level input A % (OBD PID 01C3 byte A). */
        val fuelLevelInputAPct: Float? = null,
        /** Fuel level input B % (OBD PID 01C3 byte B). */
        val fuelLevelInputBPct: Float? = null,
        /** EPCS diagnostic time sec (OBD PID 01C4 byte A). */
        val epcsDiagTimeSec: Float? = null,
        /** EPCS diagnostic count (OBD PID 01C4 byte B). */
        val epcsDiagCount: Float? = null,
        /** NOx/PCD warning lamp on (OBD PID 01C8). */
        val noxPcdLampOn: Int? = null,
        /** Particulate inducement status (OBD PID 01C6 byte A). */
        val particulateInduceStatus: Int? = null,
        /** DPF removal/block counter (OBD PID 01C6 bytes B/C). */
        val dpfRemovalCounter: Float? = null,
        /** Reagent injection failure counter (OBD PID 01C6 bytes D/E). */
        val reagentInjectionFailCounter: Float? = null,
        /** Particulate monitor malfunction counter (OBD PID 01C6 bytes F/G). */
        val particulateMonitorMalfunctionCounter: Float? = null,
        /** Engine fuel rate g/s (OBD PID 019D bytes A/B). */
        val engineFuelRateGps: Float? = null,
        /** Engine exhaust flow kg/h (OBD PID 019E). */
        val engineExhaustFlowKgh: Float? = null,
        /** Fuel system use % 1–3 (OBD PID 019F bytes B–D). */
        val fuelSysUsePct1: Float? = null,
        val fuelSysUsePct2: Float? = null,
        val fuelSysUsePct3: Float? = null,
        /** WWH-OBD continuous MI counter hours (OBD PID 0190 bytes B/C). */
        val wwhObdContinuousMiHours: Float? = null,
        /** WWH-OBD ECU B1 counter hours (OBD PID 0191 bytes D/E). */
        val wwhObdEcuB1Hours: Float? = null,
        /** Fuel system closed-loop control count (OBD PID 0192 byte B). */
        val fuelSysCtlClosedCount: Float? = null,
        /** WWH-OBD cumulative MI counter hours (OBD PID 0193 bytes B/C). */
        val wwhObdCumulativeMiHours: Float? = null,
        /** Hybrid/EV pack voltage V (OBD PID 019A bytes A/B). */
        val hybridEvBattVoltageV: Float? = null,
        /** Traction battery SOH % (OBD PID 01B2 byte A). */
        val hvBattSohPct: Float? = null,
        /** HVESS temperature °C (OBD PID 01B4 byte A). */
        val hvessTempC: Float? = null,
        /** HVESS current A (OBD PID 01B5 bytes A/B). */
        val hvessCurrentA: Float? = null,
        /** HVESS pack voltage V (OBD PID 01B6 bytes A/B). */
        val hvessVoltageV: Float? = null,
        /** HEV max cell temperature °C (OBD PID 01B7 byte B). */
        val hvCellMaxTempC: Float? = null,
        /** Hours since last cell balancing (OBD PID 01B8 bytes A/B). */
        val hvBalHours: Float? = null,
        /** HEV min cell voltage V (OBD PID 01B9 bytes A/B). */
        val hvCellMinVoltageV: Float? = null,
        /** HEV max cell voltage V (OBD PID 01B9 bytes C/D). */
        val hvCellMaxVoltageV: Float? = null,
        /** HEV continuous rated power available % (OBD PID 01BA byte A). */
        val hvPwrAvailPct: Float? = null,
        /** HEV charge current limit A (OBD PID 01BA bytes B/C). */
        val hvChgLimitA: Float? = null,
        /** HEV min cell temperature °C (OBD PID 01B7 byte A). */
        val hvCellMinTempC: Float? = null,
        /** HEV discharge current limit A (OBD PID 01BA bytes D/E). */
        val hvDisLimitA: Float? = null,
        /** Cumulative energy into HVESS kWh (OBD PID 01BB bytes A–D). */
        val hvEnrgInKwh: Float? = null,
        /** Cumulative energy from HVESS kWh (OBD PID 01BC bytes A–D). */
        val hvEnrgOutKwh: Float? = null,
        /** HVESS total energy throughput Wh (OBD PID 01BD bytes A–D). */
        val hvEnrgTputWh: Float? = null,
        /** HVESS actual charge rate kW (OBD PID 01B3 bytes A/B signed /10). */
        val hvAcrKw: Float? = null,
        /** HVESS SOH % (OBD PID 01BE byte A). */
        val hvessSohPct: Float? = null,
        /** Recommended min SOC % (OBD PID 01BF byte A). */
        val hvMinSocPct: Float? = null,
        /** Recommended max SOC % (OBD PID 01C1 byte A). */
        val hvMaxSocPct: Float? = null,
        /** Discharge energy capacity kWh (OBD PID 01C2 bytes A/B /10). */
        val hvDcapKwh: Float? = null,
        /** State of Certified Energy % (OBD PID 01D2 byte B ×100/255). */
        val hvSocePct: Float? = null,
        /** Calculated ESS energy capacity kWh (OBD PID 01D9 bytes A/B /10). */
        val essCapKwh: Float? = null,
        /** Battery capacity calculation ready 0/1 (OBD PID 01D8 byte A bit0). */
        val bcapReady: Int? = null,
        /** Remaining ESS reserve energy kWh (OBD PID 01D0 bytes A/B /10). */
        val essRsrvRemKwh: Float? = null,
        /** Initial ESS reserve energy kWh (OBD PID 01D0 bytes C/D /10). */
        val essRsrvInitKwh: Float? = null,
        /** Distance since last SOH update km (OBD PID 01D0 bytes E/F). */
        val essHealthDistKm: Float? = null,
        /** ESS charging limit kW (OBD PID 01D1 bytes A/B signed /10). */
        val essChgLimKw: Float? = null,
        /** ESS actual charging power kW (OBD PID 01D1 bytes C/D signed /10). */
        val essChgActKw: Float? = null,
        /** Battery pack energy rate Wh/s (OBD PID 01D4 bytes A/B signed /10). */
        val hvEnerRateWhs: Float? = null,
        /** Battery pack current rate Ah/s (OBD PID 01DA bytes A/B signed /100). */
        val hvCurrRateAhs: Float? = null,
        /** Electric motor A RPM (OBD PID 01CC bytes A/B). */
        val emRpmA: Float? = null,
        /** Electric motor A torque Nm (OBD PID 01CD bytes A/B signed /10). */
        val emTqANm: Float? = null,
        /** Diesel exhaust fluid % (OBD PID 019B byte D). */
        val defFluidPct: Float? = null,
        val runtimeSec: Int? = null,
        val milDistanceKm: Float? = null,
        val distSinceClearKm: Float? = null,
        val batteryVoltageV: Float? = null,
    )

    private fun u32Scaled(data: List<Int>, offset: Int, scale: Float): Float? {
        if (data.size < offset + 4) return null
        val raw =
            (data[offset].toLong() shl 24) or
                (data[offset + 1].toLong() shl 16) or
                (data[offset + 2].toLong() shl 8) or
                data[offset + 3].toLong()
        return raw / scale
    }

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
        return parseMode01Pid(pid, data)
    }

    private fun parseMode01Pid(pid: Int, data: List<Int>): PidValues {
        return when {
            pid == 0x21 || pid == 0x31 || pid == 0x42 || pid >= 0xB0 -> parseMode01Part4(pid, data)
            pid < 0x60 -> parseMode01Part1(pid, data)
            pid < 0x90 -> parseMode01Part2(pid, data)
            pid < 0xB0 -> parseMode01Part3(pid, data)
            else -> parseMode01Part4(pid, data)
        }
    }

    private fun parseMode01Part1(pid: Int, data: List<Int>): PidValues = when (pid) {
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
            0x08 ->
                PidValues(
                    fuelTrimStftB2Pct =
                        data.getOrNull(0)?.let { (it - 128) * 100f / 128f },
                )
            0x09 ->
                PidValues(
                    fuelTrimLtftB2Pct =
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
        else -> PidValues()
    }
    private fun parseMode01Part2(pid: Int, data: List<Int>): PidValues = when (pid) {
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
            0x7F -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB1s9TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x80 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB2s9TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
        else -> PidValues()
    }
    private fun parseMode01Part3(pid: Int, data: List<Int>): PidValues = when (pid) {
            0x67 -> {
                if (data.size < 3) PidValues()
                else PidValues(coolantEct2C = (data[2].minus(40)).toFloat())
            }
            0x68 -> {
                if (data.size < 3) PidValues()
                else PidValues(iatSensor2C = (data[2].minus(40)).toFloat())
            }
            0x6F -> PidValues(turboInletKpa = data.getOrNull(0)?.toFloat())
            0x81 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB1s10TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x82 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB2s10TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x6B -> {
                if (data.size < 2) PidValues()
                else PidValues(egrTempC = (data[1].minus(40)).toFloat())
            }
            0x6A -> PidValues(dieselIafCmdPct = data.getOrNull(0)?.let { it * 100f / 255f })
            0x6C -> PidValues(thrActuatorPct = data.getOrNull(0)?.let { it * 100f / 255f })
            0x83 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB1s11TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x84 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB2s11TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x85 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB1s12TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x86 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB2s12TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x87 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB1s13TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x88 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB2s13TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x89 -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB1s14TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x8A -> {
                if (data.size < 2) PidValues()
                else PidValues(catalystB2s14TempC = ((data[0] * 256) + data[1]) / 10f - 40f)
            }
            0x8C -> {
                if (data.size < 11) PidValues()
                else PidValues(o2LambdaB1 = ((data[9] * 256) + data[10]) * 0.000122f)
            }
            0x8F -> {
                val b1 = if (data.size >= 4) ((data[2] * 256) + data[3]) / 100f else null
                val b2 = if (data.size >= 7) ((data[5] * 256) + data[6]) / 100f else null
                PidValues(pmSensorB1Pct = b1, pmSensorB2Pct = b2)
            }
            0x94 -> {
                if (data.size < 4) PidValues()
                else {
                    val b = data[1]
                    PidValues(
                        noxWarningActive = if ((b and 0x01) != 0) 1 else 0,
                        noxInduceLevel1 = (b shr 1) and 0x03,
                        noxInduceLevel2 = (b shr 3) and 0x03,
                        noxReagentQualHours = ((data[2] * 256) + data[3]).toFloat(),
                        noxEgrValveCounterHours =
                            if (data.size >= 10) (data[8] * 256 + data[9]).toFloat() else null,
                        noxMonitorMalfunctionHours =
                            if (data.size >= 12) (data[10] * 256 + data[11]).toFloat() else null,
                    )
                }
            }
            0x98 -> {
                val s5 = if (data.size >= 3) ((data[1] * 256) + data[2]) / 10f - 40f else null
                val s6 = if (data.size >= 5) ((data[3] * 256) + data[4]) / 10f - 40f else null
                val s7 = if (data.size >= 7) ((data[5] * 256) + data[6]) / 10f - 40f else null
                val s8 = if (data.size >= 9) ((data[7] * 256) + data[8]) / 10f - 40f else null
                PidValues(egtB1s5TempC = s5, egtB1s6TempC = s6, egtB1s7TempC = s7, egtB1s8TempC = s8)
            }
            0x99 -> {
                val s5 = if (data.size >= 3) ((data[1] * 256) + data[2]) / 10f - 40f else null
                val s6 = if (data.size >= 5) ((data[3] * 256) + data[4]) / 10f - 40f else null
                val s7 = if (data.size >= 7) ((data[5] * 256) + data[6]) / 10f - 40f else null
                val s8 = if (data.size >= 9) ((data[7] * 256) + data[8]) / 10f - 40f else null
                PidValues(egtB2s5TempC = s5, egtB2s6TempC = s6, egtB2s7TempC = s7, egtB2s8TempC = s8)
            }
            0x9C -> {
                val cB1s3 = if (data.size >= 3) ((data[1] * 256) + data[2]) * 0.001526f else null
                val cB1s4 = if (data.size >= 5) ((data[3] * 256) + data[4]) * 0.001526f else null
                val cB2s3 = if (data.size >= 7) ((data[5] * 256) + data[6]) * 0.001526f else null
                val cB2s4 = if (data.size >= 9) ((data[7] * 256) + data[8]) * 0.001526f else null
                val b1s3 = if (data.size >= 11) ((data[9] * 256) + data[10]) * 0.000122f else null
                val b2s3 = if (data.size >= 15) ((data[13] * 256) + data[14]) * 0.000122f else null
                val b1s4 = if (data.size >= 13) ((data[11] * 256) + data[12]) * 0.000122f else null
                val b2s4 = if (data.size >= 17) ((data[15] * 256) + data[16]) * 0.000122f else null
                PidValues(
                    o2ConcB1s3Pct = cB1s3,
                    o2ConcB1s4Pct = cB1s4,
                    o2ConcB2s3Pct = cB2s3,
                    o2ConcB2s4Pct = cB2s4,
                    o2LambdaB1s3 = b1s3,
                    o2LambdaB2s3 = b2s3,
                    o2LambdaB1s4 = b1s4,
                    o2LambdaB2s4 = b2s4,
                )
            }
            0xA5 -> {
                if (data.size < 2) PidValues()
                else PidValues(defDosingCmdPct = data[1] / 2f)
            }
            0xA1 -> {
                val s1 = if (data.size >= 3) ((data[1] * 256) + data[2]).toFloat() else null
                val s2 = if (data.size >= 5) ((data[3] * 256) + data[4]).toFloat() else null
                val b2s1 = if (data.size >= 7) ((data[5] * 256) + data[6]).toFloat() else null
                val b2s2 = if (data.size >= 9) ((data[7] * 256) + data[8]).toFloat() else null
                PidValues(
                    noxCorrectedB1s1Ppm = s1,
                    noxCorrectedB1s2Ppm = s2,
                    noxCorrectedB2s1Ppm = b2s1,
                    noxCorrectedB2s2Ppm = b2s2,
                )
            }
            0xA7 -> {
                if (data.size < 4) PidValues()
                else PidValues(
                    noxConcS3Ppm = ((data[0] * 256) + data[1]).toFloat(),
                    noxConcS4Ppm = ((data[2] * 256) + data[3]).toFloat(),
                )
            }
            0xA8 -> {
                if (data.size < 4) PidValues()
                else PidValues(
                    noxCorrectedS3Ppm = ((data[0] * 256) + data[1]).toFloat(),
                    noxCorrectedS4Ppm = ((data[2] * 256) + data[3]).toFloat(),
                )
            }
            0xA2 -> {
                if (data.size < 2) PidValues()
                else PidValues(cylinderFuelRateMg = ((data[0] * 256) + data[1]) / 32f)
            }
            0xA3 -> {
                if (data.size < 3) PidValues()
                else {
                    val raw = (data[1] shl 8) or data[2]
                    val signed = if (raw and 0x8000 != 0) raw - 0x10000 else raw
                    PidValues(evapSysVaporPa = signed.toFloat())
                }
            }
            0xA4 -> {
                if (data.size < 4 || (data[0] and 0x02) == 0) PidValues()
                else PidValues(transGearRatio = ((data[2] * 256) + data[3]) / 1000f)
            }
            0xA6 -> {
                if (data.size < 4) PidValues()
                else {
                    val raw =
                        ((data[0].toLong() shl 24) or (data[1].toLong() shl 16) or
                            (data[2].toLong() shl 8) or data[3].toLong())
                    PidValues(obdOdometerKm = raw / 10f)
                }
            }
            0xA9 -> {
                if (data.size < 2) PidValues()
                else PidValues(
                    absDisableSupported = if ((data[0] and 0x01) != 0) 1 else 0,
                    absDisabled = if ((data[1] and 0x01) != 0) 1 else 0,
                )
            }
            0xC5 -> {
                if (data.size < 4) PidValues()
                else PidValues(
                    fuelPressAKpa = ((data[0] * 256) + data[1]).toFloat(),
                    fuelPressBKpa = ((data[2] * 256) + data[3]).toFloat(),
                )
            }
            0xC7 -> {
                if (data.size < 2) PidValues()
                else PidValues(reflashDistKm = ((data[0] * 256) + data[1]).toFloat())
            }
            0xC3 -> {
                if (data.size < 2) PidValues()
                else PidValues(
                    fuelLevelInputAPct = data[0] * 100f / 255f,
                    fuelLevelInputBPct = data[1] * 100f / 255f,
                )
            }
        else -> PidValues()
    }
    private fun parseMode01Part4(pid: Int, data: List<Int>): PidValues = when (pid) {
            0xC4 -> {
                if (data.size < 2) PidValues()
                else PidValues(
                    epcsDiagTimeSec = data[0].toFloat(),
                    epcsDiagCount = data[1].toFloat(),
                )
            }
            0xC8 -> {
                if (data.isEmpty()) PidValues()
                else PidValues(noxPcdLampOn = if ((data[0] and 0x03) != 0) 1 else 0)
            }
            0xC6 -> {
                if (data.size < 7) PidValues()
                else PidValues(
                    particulateInduceStatus = data[0],
                    dpfRemovalCounter = (data[1] * 256 + data[2]).toFloat(),
                    reagentInjectionFailCounter = (data[3] * 256 + data[4]).toFloat(),
                    particulateMonitorMalfunctionCounter = (data[5] * 256 + data[6]).toFloat(),
                )
            }
            0x90 -> {
                if (data.size < 3) PidValues()
                else PidValues(wwhObdContinuousMiHours = (data[1] * 256 + data[2]).toFloat())
            }
            0x91 -> {
                if (data.size < 5) PidValues()
                else PidValues(wwhObdEcuB1Hours = (data[3] * 256 + data[4]).toFloat())
            }
            0x92 -> {
                if (data.size < 2) PidValues()
                else PidValues(fuelSysCtlClosedCount = Integer.bitCount(data[1] and 0xFF).toFloat())
            }
            0x93 -> {
                if (data.size < 3) PidValues()
                else PidValues(wwhObdCumulativeMiHours = (data[1] * 256 + data[2]).toFloat())
            }
            0x9A -> {
                if (data.size < 2) PidValues()
                else PidValues(hybridEvBattVoltageV = (data[0] * 256 + data[1]) / 10f)
            }
            0xB2 -> {
                if (data.isEmpty()) PidValues()
                else PidValues(hvBattSohPct = data[0] * 100f / 255f)
            }
            0xB3 -> {
                if (data.size < 2) PidValues()
                else {
                    val raw = (data[0] shl 8) or data[1]
                    val signed = if (raw and 0x8000 != 0) raw - 0x10000 else raw
                    PidValues(hvAcrKw = signed / 10f)
                }
            }
            0xB4 -> {
                if (data.isEmpty()) PidValues()
                else PidValues(hvessTempC = data[0] - 40f)
            }
            0xB5 -> {
                if (data.size < 2) PidValues()
                else {
                    val raw = (data[0] shl 8) or data[1]
                    val signed = if (raw and 0x8000 != 0) raw - 0x10000 else raw
                    PidValues(hvessCurrentA = signed / 10f)
                }
            }
            0xB6 -> {
                if (data.size < 2) PidValues()
                else PidValues(hvessVoltageV = (data[0] * 256 + data[1]) / 10f)
            }
            0xB7 -> {
                if (data.size < 2) PidValues()
                else PidValues(hvCellMinTempC = data[0] - 40f, hvCellMaxTempC = data[1] - 40f)
            }
            0xB8 -> {
                if (data.size < 2) PidValues()
                else PidValues(hvBalHours = (data[0] * 256 + data[1]).toFloat())
            }
            0xB9 -> {
                val minV = if (data.size >= 2) ((data[0] * 256) + data[1]) / 1666.666f else null
                val maxV = if (data.size >= 4) ((data[2] * 256) + data[3]) / 1666.666f else null
                PidValues(hvCellMinVoltageV = minV, hvCellMaxVoltageV = maxV)
            }
            0xBA -> {
                if (data.isEmpty()) PidValues()
                else {
                    val chg =
                        if (data.size >= 3) {
                            val raw = (data[1] shl 8) or data[2]
                            val signed = if (raw and 0x8000 != 0) raw - 0x10000 else raw
                            signed / 10f
                        } else null
                    val dis =
                        if (data.size >= 5) {
                            val raw = (data[3] shl 8) or data[4]
                            val signed = if (raw and 0x8000 != 0) raw - 0x10000 else raw
                            signed / 10f
                        } else null
                    PidValues(
                        hvPwrAvailPct = data[0] * 100f / 255f,
                        hvChgLimitA = chg,
                        hvDisLimitA = dis,
                    )
                }
            }
            0xBB -> {
                val kwh = u32Scaled(data, 0, 10f)
                if (kwh == null) PidValues() else PidValues(hvEnrgInKwh = kwh)
            }
            0xBC -> {
                val kwh = u32Scaled(data, 0, 10f)
                if (kwh == null) PidValues() else PidValues(hvEnrgOutKwh = kwh)
            }
            0xBD -> {
                val wh = u32Scaled(data, 0, 10f)
                if (wh == null) PidValues() else PidValues(hvEnrgTputWh = wh)
            }
            0xBE -> {
                if (data.isEmpty()) PidValues()
                else PidValues(hvessSohPct = data[0] * 100f / 255f)
            }
            0xBF -> {
                if (data.isEmpty()) PidValues()
                else PidValues(hvMinSocPct = data[0] * 100f / 255f)
            }
            0xC1 -> {
                if (data.isEmpty()) PidValues()
                else PidValues(hvMaxSocPct = data[0] * 100f / 255f)
            }
            0xC2 -> {
                if (data.size < 2) PidValues()
                else PidValues(hvDcapKwh = ((data[0] * 256) + data[1]) / 10f)
            }
            0xD2 -> {
                if (data.size < 2) PidValues()
                else PidValues(hvSocePct = data[1] * 100f / 255f)
            }
            0xD9 -> {
                if (data.size < 2) PidValues()
                else PidValues(essCapKwh = ((data[0] * 256) + data[1]) / 10f)
            }
            0xD8 -> {
                if (data.isEmpty()) PidValues()
                else PidValues(bcapReady = if ((data[0] and 0x01) != 0) 1 else 0)
            }
            0xD0 -> {
                if (data.size < 2) PidValues()
                else {
                    val rem = ((data[0] * 256) + data[1]) / 10f
                    val init = if (data.size >= 4) ((data[2] * 256) + data[3]) / 10f else null
                    val dist = if (data.size >= 6) ((data[4] * 256) + data[5]).toFloat() else null
                    PidValues(essRsrvRemKwh = rem, essRsrvInitKwh = init, essHealthDistKm = dist)
                }
            }
            0xD1 -> {
                if (data.size < 2) PidValues()
                else {
                    fun signedAb(hi: Int, lo: Int): Float {
                        val raw = (hi shl 8) or lo
                        val signed = if (raw and 0x8000 != 0) raw - 0x10000 else raw
                        return signed / 10f
                    }
                    val lim = signedAb(data[0], data[1])
                    val act = if (data.size >= 4) signedAb(data[2], data[3]) else null
                    PidValues(essChgLimKw = lim, essChgActKw = act)
                }
            }
            0xD4 -> {
                if (data.size < 2) PidValues()
                else {
                    val raw = (data[0] shl 8) or data[1]
                    val signed = if (raw and 0x8000 != 0) raw - 0x10000 else raw
                    PidValues(hvEnerRateWhs = signed / 10f)
                }
            }
            0xDA -> {
                if (data.size < 2) PidValues()
                else {
                    val raw = (data[0] shl 8) or data[1]
                    val signed = if (raw and 0x8000 != 0) raw - 0x10000 else raw
                    PidValues(hvCurrRateAhs = signed / 100f)
                }
            }
            0xCC -> {
                if (data.size < 2) PidValues()
                else PidValues(emRpmA = ((data[0] * 256) + data[1]).toFloat())
            }
            0xCD -> {
                if (data.size < 2) PidValues()
                else {
                    val raw = (data[0] shl 8) or data[1]
                    val signed = if (raw and 0x8000 != 0) raw - 0x10000 else raw
                    PidValues(emTqANm = signed / 10f)
                }
            }
            0x9D -> {
                if (data.size < 2) PidValues()
                else PidValues(engineFuelRateGps = (data[0] * 256 + data[1]) / 200f)
            }
            0x9E -> {
                if (data.size < 2) PidValues()
                else PidValues(engineExhaustFlowKgh = (data[0] * 256 + data[1]) / 20f)
            }
            0x9F -> {
                if (data.size < 4) PidValues()
                else PidValues(
                    fuelSysUsePct1 = data[1] * 100f / 255f,
                    fuelSysUsePct2 = data[2] * 100f / 255f,
                    fuelSysUsePct3 = data[3] * 100f / 255f,
                )
            }
            0x9B -> {
                if (data.size < 4) PidValues()
                else PidValues(defFluidPct = data[3] * 100f / 255f)
            }
            0x8B -> {
                if (data.size < 3) PidValues()
                else PidValues(dpfTriggerPct = data[2] * 100f / 255f)
            }
            0x8D -> PidValues(throttleGPct = data.getOrNull(0)?.let { it * 100f / 255f })
            0x8E -> PidValues(engineFrictionPct = data.getOrNull(0)?.let { (it - 125).toFloat() })
            0x69 -> PidValues(actualEgrPct = data.getOrNull(0)?.let { it * 100f / 255f })
            0x6E -> {
                if (data.size < 2) PidValues()
                else PidValues(injectCtrlKpa = ((data[0] * 256) + data[1]) / 10f)
            }
            0x6D -> {
                if (data.size < 2) PidValues()
                else PidValues(fuelCtrlKpa = ((data[0] * 256) + data[1]) / 10f)
            }
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
            catalystB1s9TempC = add.catalystB1s9TempC ?: base.catalystB1s9TempC,
            catalystB2s9TempC = add.catalystB2s9TempC ?: base.catalystB2s9TempC,
            coolantEct2C = add.coolantEct2C ?: base.coolantEct2C,
            iatSensor2C = add.iatSensor2C ?: base.iatSensor2C,
            turboInletKpa = add.turboInletKpa ?: base.turboInletKpa,
            catalystB1s10TempC = add.catalystB1s10TempC ?: base.catalystB1s10TempC,
            catalystB2s10TempC = add.catalystB2s10TempC ?: base.catalystB2s10TempC,
            egrTempC = add.egrTempC ?: base.egrTempC,
            dieselIafCmdPct = add.dieselIafCmdPct ?: base.dieselIafCmdPct,
            thrActuatorPct = add.thrActuatorPct ?: base.thrActuatorPct,
            catalystB1s11TempC = add.catalystB1s11TempC ?: base.catalystB1s11TempC,
            catalystB2s11TempC = add.catalystB2s11TempC ?: base.catalystB2s11TempC,
            actualEgrPct = add.actualEgrPct ?: base.actualEgrPct,
            injectCtrlKpa = add.injectCtrlKpa ?: base.injectCtrlKpa,
            fuelCtrlKpa = add.fuelCtrlKpa ?: base.fuelCtrlKpa,
            catalystB1s12TempC = add.catalystB1s12TempC ?: base.catalystB1s12TempC,
            catalystB2s12TempC = add.catalystB2s12TempC ?: base.catalystB2s12TempC,
            fuelTrimStftB2Pct = add.fuelTrimStftB2Pct ?: base.fuelTrimStftB2Pct,
            fuelTrimLtftB2Pct = add.fuelTrimLtftB2Pct ?: base.fuelTrimLtftB2Pct,
            catalystB1s13TempC = add.catalystB1s13TempC ?: base.catalystB1s13TempC,
            catalystB2s13TempC = add.catalystB2s13TempC ?: base.catalystB2s13TempC,
            dpfTriggerPct = add.dpfTriggerPct ?: base.dpfTriggerPct,
            throttleGPct = add.throttleGPct ?: base.throttleGPct,
            engineFrictionPct = add.engineFrictionPct ?: base.engineFrictionPct,
            catalystB1s14TempC = add.catalystB1s14TempC ?: base.catalystB1s14TempC,
            catalystB2s14TempC = add.catalystB2s14TempC ?: base.catalystB2s14TempC,
            o2LambdaB1 = add.o2LambdaB1 ?: base.o2LambdaB1,
            pmSensorB1Pct = add.pmSensorB1Pct ?: base.pmSensorB1Pct,
            pmSensorB2Pct = add.pmSensorB2Pct ?: base.pmSensorB2Pct,
            egtB1s5TempC = add.egtB1s5TempC ?: base.egtB1s5TempC,
            egtB2s5TempC = add.egtB2s5TempC ?: base.egtB2s5TempC,
            o2LambdaB1s3 = add.o2LambdaB1s3 ?: base.o2LambdaB1s3,
            o2LambdaB2s3 = add.o2LambdaB2s3 ?: base.o2LambdaB2s3,
            noxReagentQualHours = add.noxReagentQualHours ?: base.noxReagentQualHours,
            noxWarningActive = add.noxWarningActive ?: base.noxWarningActive,
            noxInduceLevel1 = add.noxInduceLevel1 ?: base.noxInduceLevel1,
            noxInduceLevel2 = add.noxInduceLevel2 ?: base.noxInduceLevel2,
            noxEgrValveCounterHours = add.noxEgrValveCounterHours ?: base.noxEgrValveCounterHours,
            noxMonitorMalfunctionHours = add.noxMonitorMalfunctionHours ?: base.noxMonitorMalfunctionHours,
            egtB1s6TempC = add.egtB1s6TempC ?: base.egtB1s6TempC,
            egtB2s6TempC = add.egtB2s6TempC ?: base.egtB2s6TempC,
            egtB1s7TempC = add.egtB1s7TempC ?: base.egtB1s7TempC,
            egtB2s7TempC = add.egtB2s7TempC ?: base.egtB2s7TempC,
            egtB1s8TempC = add.egtB1s8TempC ?: base.egtB1s8TempC,
            egtB2s8TempC = add.egtB2s8TempC ?: base.egtB2s8TempC,
            o2LambdaB1s4 = add.o2LambdaB1s4 ?: base.o2LambdaB1s4,
            o2LambdaB2s4 = add.o2LambdaB2s4 ?: base.o2LambdaB2s4,
            o2ConcB1s3Pct = add.o2ConcB1s3Pct ?: base.o2ConcB1s3Pct,
            o2ConcB1s4Pct = add.o2ConcB1s4Pct ?: base.o2ConcB1s4Pct,
            o2ConcB2s3Pct = add.o2ConcB2s3Pct ?: base.o2ConcB2s3Pct,
            o2ConcB2s4Pct = add.o2ConcB2s4Pct ?: base.o2ConcB2s4Pct,
            defDosingCmdPct = add.defDosingCmdPct ?: base.defDosingCmdPct,
            noxCorrectedB1s1Ppm = add.noxCorrectedB1s1Ppm ?: base.noxCorrectedB1s1Ppm,
            noxCorrectedB1s2Ppm = add.noxCorrectedB1s2Ppm ?: base.noxCorrectedB1s2Ppm,
            noxCorrectedB2s1Ppm = add.noxCorrectedB2s1Ppm ?: base.noxCorrectedB2s1Ppm,
            noxCorrectedB2s2Ppm = add.noxCorrectedB2s2Ppm ?: base.noxCorrectedB2s2Ppm,
            noxConcS3Ppm = add.noxConcS3Ppm ?: base.noxConcS3Ppm,
            noxConcS4Ppm = add.noxConcS4Ppm ?: base.noxConcS4Ppm,
            noxCorrectedS3Ppm = add.noxCorrectedS3Ppm ?: base.noxCorrectedS3Ppm,
            noxCorrectedS4Ppm = add.noxCorrectedS4Ppm ?: base.noxCorrectedS4Ppm,
            cylinderFuelRateMg = add.cylinderFuelRateMg ?: base.cylinderFuelRateMg,
            evapSysVaporPa = add.evapSysVaporPa ?: base.evapSysVaporPa,
            transGearRatio = add.transGearRatio ?: base.transGearRatio,
            obdOdometerKm = add.obdOdometerKm ?: base.obdOdometerKm,
            absDisableSupported = add.absDisableSupported ?: base.absDisableSupported,
            absDisabled = add.absDisabled ?: base.absDisabled,
            fuelPressAKpa = add.fuelPressAKpa ?: base.fuelPressAKpa,
            fuelPressBKpa = add.fuelPressBKpa ?: base.fuelPressBKpa,
            reflashDistKm = add.reflashDistKm ?: base.reflashDistKm,
            fuelLevelInputAPct = add.fuelLevelInputAPct ?: base.fuelLevelInputAPct,
            fuelLevelInputBPct = add.fuelLevelInputBPct ?: base.fuelLevelInputBPct,
            epcsDiagTimeSec = add.epcsDiagTimeSec ?: base.epcsDiagTimeSec,
            epcsDiagCount = add.epcsDiagCount ?: base.epcsDiagCount,
            noxPcdLampOn = add.noxPcdLampOn ?: base.noxPcdLampOn,
            particulateInduceStatus = add.particulateInduceStatus ?: base.particulateInduceStatus,
            dpfRemovalCounter = add.dpfRemovalCounter ?: base.dpfRemovalCounter,
            reagentInjectionFailCounter = add.reagentInjectionFailCounter ?: base.reagentInjectionFailCounter,
            particulateMonitorMalfunctionCounter = add.particulateMonitorMalfunctionCounter ?: base.particulateMonitorMalfunctionCounter,
            engineFuelRateGps = add.engineFuelRateGps ?: base.engineFuelRateGps,
            engineExhaustFlowKgh = add.engineExhaustFlowKgh ?: base.engineExhaustFlowKgh,
            fuelSysUsePct1 = add.fuelSysUsePct1 ?: base.fuelSysUsePct1,
            fuelSysUsePct2 = add.fuelSysUsePct2 ?: base.fuelSysUsePct2,
            fuelSysUsePct3 = add.fuelSysUsePct3 ?: base.fuelSysUsePct3,
            wwhObdContinuousMiHours = add.wwhObdContinuousMiHours ?: base.wwhObdContinuousMiHours,
            wwhObdEcuB1Hours = add.wwhObdEcuB1Hours ?: base.wwhObdEcuB1Hours,
            fuelSysCtlClosedCount = add.fuelSysCtlClosedCount ?: base.fuelSysCtlClosedCount,
            wwhObdCumulativeMiHours = add.wwhObdCumulativeMiHours ?: base.wwhObdCumulativeMiHours,
            hybridEvBattVoltageV = add.hybridEvBattVoltageV ?: base.hybridEvBattVoltageV,
            hvBattSohPct = add.hvBattSohPct ?: base.hvBattSohPct,
            hvessTempC = add.hvessTempC ?: base.hvessTempC,
            hvessCurrentA = add.hvessCurrentA ?: base.hvessCurrentA,
            hvessVoltageV = add.hvessVoltageV ?: base.hvessVoltageV,
            hvCellMaxTempC = add.hvCellMaxTempC ?: base.hvCellMaxTempC,
            hvBalHours = add.hvBalHours ?: base.hvBalHours,
            hvCellMinVoltageV = add.hvCellMinVoltageV ?: base.hvCellMinVoltageV,
            hvCellMaxVoltageV = add.hvCellMaxVoltageV ?: base.hvCellMaxVoltageV,
            hvPwrAvailPct = add.hvPwrAvailPct ?: base.hvPwrAvailPct,
            hvChgLimitA = add.hvChgLimitA ?: base.hvChgLimitA,
            hvCellMinTempC = add.hvCellMinTempC ?: base.hvCellMinTempC,
            hvDisLimitA = add.hvDisLimitA ?: base.hvDisLimitA,
            hvEnrgInKwh = add.hvEnrgInKwh ?: base.hvEnrgInKwh,
            hvEnrgOutKwh = add.hvEnrgOutKwh ?: base.hvEnrgOutKwh,
            hvEnrgTputWh = add.hvEnrgTputWh ?: base.hvEnrgTputWh,
            hvAcrKw = add.hvAcrKw ?: base.hvAcrKw,
            hvessSohPct = add.hvessSohPct ?: base.hvessSohPct,
            hvMinSocPct = add.hvMinSocPct ?: base.hvMinSocPct,
            hvMaxSocPct = add.hvMaxSocPct ?: base.hvMaxSocPct,
            hvDcapKwh = add.hvDcapKwh ?: base.hvDcapKwh,
            hvSocePct = add.hvSocePct ?: base.hvSocePct,
            essCapKwh = add.essCapKwh ?: base.essCapKwh,
            bcapReady = add.bcapReady ?: base.bcapReady,
            essRsrvRemKwh = add.essRsrvRemKwh ?: base.essRsrvRemKwh,
            essRsrvInitKwh = add.essRsrvInitKwh ?: base.essRsrvInitKwh,
            essHealthDistKm = add.essHealthDistKm ?: base.essHealthDistKm,
            essChgLimKw = add.essChgLimKw ?: base.essChgLimKw,
            essChgActKw = add.essChgActKw ?: base.essChgActKw,
            hvEnerRateWhs = add.hvEnerRateWhs ?: base.hvEnerRateWhs,
            hvCurrRateAhs = add.hvCurrRateAhs ?: base.hvCurrRateAhs,
            emRpmA = add.emRpmA ?: base.emRpmA,
            emTqANm = add.emTqANm ?: base.emTqANm,
            defFluidPct = add.defFluidPct ?: base.defFluidPct,
            runtimeSec = add.runtimeSec ?: base.runtimeSec,
            milDistanceKm = add.milDistanceKm ?: base.milDistanceKm,
            distSinceClearKm = add.distSinceClearKm ?: base.distSinceClearKm,
            batteryVoltageV = add.batteryVoltageV ?: base.batteryVoltageV,
        )
}
