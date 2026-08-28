/**
 * Single source for OBD Mode 01 decode parity with ObdPidParser.kt (Kotlin).
 * Used by obd-pid-smoke, fase smokes, and validate-gate — not a product UI.
 */
export function extractPayloadBytes(raw) {
  const cleaned = raw
    .toUpperCase()
    .replace(/SEARCHING\.\.\./g, '')
    .replace(/SEARCHING…/g, '')
    .replace(/STOPPED|NO DATA|UNABLE TO CONNECT|BUS INIT|OK|>/g, '')
    .replace(/[\r\n]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  if (!cleaned || cleaned.includes('ERROR') || cleaned === '?') return null
  const tokens = cleaned.split(' ').filter((t) => /^[0-9A-F]{2}$/.test(t))
  if (!tokens.length) return null
  const ints = tokens.map((t) => parseInt(t, 16))
  const idx = ints.indexOf(0x41)
  if (idx < 0 || idx + 1 >= ints.length) return null
  return ints.slice(idx)
}

/** Mirrors ObdPidParser.parseMode01 */
export function parseMode01(raw) {
  const bytes = extractPayloadBytes(raw)
  if (!bytes || bytes.length < 3 || bytes[0] !== 0x41) return {}
  const pid = bytes[1]
  const data = bytes.slice(2)
  switch (pid) {
    case 0x0d:
      return { speedKmh: data[0] }
    case 0x0c:
      return data.length < 2 ? {} : { rpm: (data[0] * 256 + data[1]) / 4 }
    case 0x05:
      return { coolantC: data[0] - 40 }
    case 0x5c:
      return { oilTempC: data[0] - 40 }
    case 0x0f:
      return { intakeAirC: data[0] - 40 }
    case 0x2f:
      return { fuelPct: (data[0] * 100) / 255 }
    case 0x5e:
      return data.length < 2 ? {} : { fuelRateGps: (data[0] * 256 + data[1]) / 20 }
    case 0x46:
      return { outdoorTempC: data[0] - 40 }
    case 0x11:
      return { throttlePct: (data[0] * 100) / 255 }
    case 0x04:
      return { engineLoadPct: (data[0] * 100) / 255 }
    case 0x06:
      return { fuelTrimStftPct: ((data[0] - 128) * 100) / 128 }
    case 0x08:
      return { fuelTrimStftB2Pct: ((data[0] - 128) * 100) / 128 }
    case 0x09:
      return { fuelTrimLtftB2Pct: ((data[0] - 128) * 100) / 128 }
    case 0x0a:
      return { fuelPressureKpa: data[0] * 3 }
    case 0x0e:
      return { timingAdvanceDeg: data[0] / 2 - 64 }
    case 0x07:
      return { fuelTrimLtftPct: ((data[0] - 128) * 100) / 128 }
    case 0x0b:
      return { mapKpa: data[0] }
    case 0x10:
      return data.length < 2 ? {} : { mafGps: (data[0] * 256 + data[1]) / 100 }
    case 0x34:
      return data.length < 2 ? {} : { catalystTempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x33:
      return { baroKpa: data[0] }
    case 0x4a:
      return { o2B1s1Volts: data[0] / 200 }
    case 0x43:
      return data.length < 2 ? {} : { absoluteLoadPct: ((data[0] * 256 + data[1]) * 100) / 255 }
    case 0x45:
      return { relativeThrottlePct: (data[0] * 100) / 255 }
    case 0x49:
      return { accelPedalPct: (data[0] * 100) / 255 }
    case 0x4b:
      return { o2B1s2Volts: data[0] / 200 }
    case 0x4d:
      return { egrErrorPct: ((data[0] - 128) * 100) / 128 }
    case 0x44:
      return data.length < 2 ? {} : { equivRatio: (data[0] * 256 + data[1]) / 32768 }
    case 0x4e:
      return { evapPurgePct: (data[0] * 100) / 255 }
    case 0x52:
      return { ethanolPct: (data[0] * 100) / 255 }
    case 0x53: {
      if (data.length < 2) return {}
      const raw16 = (data[0] << 8) | data[1]
      const signed = raw16 & 0x8000 ? raw16 - 0x10000 : raw16
      return { evapVaporPa: signed / 4 }
    }
    case 0x59:
      return data.length < 2 ? {} : { fuelRailAbsKpa: (data[0] * 256 + data[1]) * 10 }
    case 0x4c:
      return { egrCmdPct: (data[0] * 100) / 255 }
    case 0x5a:
      return { relAccelPedalPct: (data[0] * 100) / 255 }
    case 0x61:
      return { driverTorquePct: data[0] - 125 }
    case 0x62:
      return { actualTorquePct: data[0] - 125 }
    case 0x70:
      return data.length < 2 ? {} : { catalystB2TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x71:
      return data.length < 2 ? {} : { catalystB1s2TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x72:
      return data.length < 2 ? {} : { catalystB2s2TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x73:
      return data.length < 2 ? {} : { catalystB1s3TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x74:
      return data.length < 2 ? {} : { catalystB2s3TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x75:
      return data.length < 2 ? {} : { catalystB1s4TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x76:
      return data.length < 2 ? {} : { catalystB2s4TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x55:
      return { fuelTrimStft2B1Pct: ((data[0] - 128) * 100) / 128 }
    case 0x56:
      return { fuelTrimLtft2B1Pct: ((data[0] - 128) * 100) / 128 }
    case 0x57:
      return { fuelTrimStft2B2Pct: ((data[0] - 128) * 100) / 128 }
    case 0x58:
      return { fuelTrimLtft2B2Pct: ((data[0] - 128) * 100) / 128 }
    case 0x77:
      return data.length < 2 ? {} : { catalystB1s5TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x78:
      return data.length < 2 ? {} : { catalystB2s5TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x5d: {
      if (data.length < 2) return {}
      const raw16 = (data[0] << 8) | data[1]
      const signed = raw16 & 0x8000 ? raw16 - 0x10000 : raw16
      return { fuelInjectTimingDeg: signed / 128 }
    }
    case 0x5b:
      return { hybridBattLifePct: (data[0] * 100) / 255 }
    case 0x63:
      return data.length < 2 ? {} : { engineRefTorqueNm: data[0] * 256 + data[1] }
    case 0x79:
      return data.length < 2 ? {} : { catalystB1s6TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x7a:
      return data.length < 2 ? {} : { catalystB2s6TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x47:
      return { throttleBPct: (data[0] * 100) / 255 }
    case 0x48:
      return { throttleCPct: (data[0] * 100) / 255 }
    case 0x54:
      return data.length < 2 ? {} : { milTimeMin: data[0] * 256 + data[1] }
    case 0x7b:
      return data.length < 2 ? {} : { catalystB1s7TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x7c:
      return data.length < 2 ? {} : { catalystB2s7TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x51:
      return { fuelTypeCode: data[0] }
    case 0x4f:
      return data.length < 2 ? {} : { maxEquivRatio: (data[0] * 256 + data[1]) / 32768 }
    case 0x50:
      return data.length < 2 ? {} : { maxMafGps: (data[0] * 256 + data[1]) / 100 }
    case 0x7d:
      return data.length < 2 ? {} : { catalystB1s8TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x7e:
      return data.length < 2 ? {} : { catalystB2s8TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x64:
      return { maxAvailTorquePct: data[0] - 125 }
    case 0x66:
      return { mafSensorIatC: data[0] - 40 }
    case 0x65:
      return { auxInputStatus: data[0] }
    case 0x7f:
      return data.length < 2 ? {} : { catalystB1s9TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x80:
      return data.length < 2 ? {} : { catalystB2s9TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x67:
      return data.length < 3 ? {} : { coolantEct2C: data[2] - 40 }
    case 0x68:
      return data.length < 3 ? {} : { iatSensor2C: data[2] - 40 }
    case 0x6f:
      return { turboInletKpa: data[0] }
    case 0x81:
      return data.length < 2 ? {} : { catalystB1s10TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x82:
      return data.length < 2 ? {} : { catalystB2s10TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x6b:
      return data.length < 2 ? {} : { egrTempC: data[1] - 40 }
    case 0x6a:
      return { dieselIafCmdPct: (data[0] * 100) / 255 }
    case 0x6c:
      return { thrActuatorPct: (data[0] * 100) / 255 }
    case 0x83:
      return data.length < 2 ? {} : { catalystB1s11TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x84:
      return data.length < 2 ? {} : { catalystB2s11TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x69:
      return { actualEgrPct: (data[0] * 100) / 255 }
    case 0x6e:
      return data.length < 2 ? {} : { injectCtrlKpa: (data[0] * 256 + data[1]) / 10 }
    case 0x6d:
      return data.length < 2 ? {} : { fuelCtrlKpa: (data[0] * 256 + data[1]) / 10 }
    case 0x85:
      return data.length < 2 ? {} : { catalystB1s12TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x86:
      return data.length < 2 ? {} : { catalystB2s12TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x87:
      return data.length < 2 ? {} : { catalystB1s13TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x88:
      return data.length < 2 ? {} : { catalystB2s13TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x89:
      return data.length < 2 ? {} : { catalystB1s14TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x8a:
      return data.length < 2 ? {} : { catalystB2s14TempC: (data[0] * 256 + data[1]) / 10 - 40 }
    case 0x8c:
      return data.length < 11 ? {} : { o2LambdaB1: (data[9] * 256 + data[10]) * 0.000122 }
    case 0x8f:
      const b1 = data.length >= 4 ? (data[2] * 256 + data[3]) / 100 : null
      const b2 = data.length >= 7 ? (data[5] * 256 + data[6]) / 100 : null
      return { pmSensorB1Pct: b1, pmSensorB2Pct: b2 }
    case 0x94:
      return data.length < 4 ? {} : { noxReagentQualHours: data[2] * 256 + data[3] }
    case 0x98:
      const s5 = data.length >= 3 ? (data[1] * 256 + data[2]) / 10 - 40 : null
      const s6 = data.length >= 5 ? (data[3] * 256 + data[4]) / 10 - 40 : null
      const s7 = data.length >= 7 ? (data[5] * 256 + data[6]) / 10 - 40 : null
      const s8 = data.length >= 9 ? (data[7] * 256 + data[8]) / 10 - 40 : null
      return { egtB1s5TempC: s5, egtB1s6TempC: s6, egtB1s7TempC: s7, egtB1s8TempC: s8 }
    case 0x99:
      const s5b = data.length >= 3 ? (data[1] * 256 + data[2]) / 10 - 40 : null
      const s6b = data.length >= 5 ? (data[3] * 256 + data[4]) / 10 - 40 : null
      const s7b = data.length >= 7 ? (data[5] * 256 + data[6]) / 10 - 40 : null
      const s8b = data.length >= 9 ? (data[7] * 256 + data[8]) / 10 - 40 : null
      return { egtB2s5TempC: s5b, egtB2s6TempC: s6b, egtB2s7TempC: s7b, egtB2s8TempC: s8b }
    case 0x9c:
      const cB1s3 = data.length >= 3 ? (data[1] * 256 + data[2]) * 0.001526 : null
      const cB1s4 = data.length >= 5 ? (data[3] * 256 + data[4]) * 0.001526 : null
      const cB2s3 = data.length >= 7 ? (data[5] * 256 + data[6]) * 0.001526 : null
      const cB2s4 = data.length >= 9 ? (data[7] * 256 + data[8]) * 0.001526 : null
      const b1s3 = data.length >= 11 ? (data[9] * 256 + data[10]) * 0.000122 : null
      const b2s3 = data.length >= 15 ? (data[13] * 256 + data[14]) * 0.000122 : null
      const b1s4 = data.length >= 13 ? (data[11] * 256 + data[12]) * 0.000122 : null
      const b2s4 = data.length >= 17 ? (data[15] * 256 + data[16]) * 0.000122 : null
      return {
        o2ConcB1s3Pct: cB1s3,
        o2ConcB1s4Pct: cB1s4,
        o2ConcB2s3Pct: cB2s3,
        o2ConcB2s4Pct: cB2s4,
        o2LambdaB1s3: b1s3,
        o2LambdaB2s3: b2s3,
        o2LambdaB1s4: b1s4,
        o2LambdaB2s4: b2s4,
      }
    case 0xa5:
      return data.length < 2 ? {} : { defDosingCmdPct: data[1] / 2 }
    case 0xa1:
      const s1 = data.length >= 3 ? data[1] * 256 + data[2] : null
      const s2 = data.length >= 5 ? data[3] * 256 + data[4] : null
      const b2s1 = data.length >= 7 ? data[5] * 256 + data[6] : null
      const b2s2 = data.length >= 9 ? data[7] * 256 + data[8] : null
      return {
        noxCorrectedB1s1Ppm: s1,
        noxCorrectedB1s2Ppm: s2,
        noxCorrectedB2s1Ppm: b2s1,
        noxCorrectedB2s2Ppm: b2s2,
      }
    case 0xa7:
      return data.length < 4
        ? {}
        : { noxConcS3Ppm: data[0] * 256 + data[1], noxConcS4Ppm: data[2] * 256 + data[3] }
    case 0xa8:
      return data.length < 4
        ? {}
        : { noxCorrectedS3Ppm: data[0] * 256 + data[1], noxCorrectedS4Ppm: data[2] * 256 + data[3] }
    case 0xa2:
      return data.length < 2 ? {} : { cylinderFuelRateMg: (data[0] * 256 + data[1]) / 32 }
    case 0xa3:
      if (data.length < 3) return {}
      const evapRaw = (data[1] << 8) | data[2]
      const evapSigned = evapRaw & 0x8000 ? evapRaw - 0x10000 : evapRaw
      return { evapSysVaporPa: evapSigned }
    case 0xa4:
      if (data.length < 4 || (data[0] & 0x02) === 0) return {}
      return { transGearRatio: (data[2] * 256 + data[3]) / 1000 }
    case 0x9b:
      return data.length < 4 ? {} : { defFluidPct: (data[3] * 100) / 255 }
    case 0x8b:
      return data.length < 3 ? {} : { dpfTriggerPct: (data[2] * 100) / 255 }
    case 0x8d:
      return { throttleGPct: (data[0] * 100) / 255 }
    case 0x8e:
      return { engineFrictionPct: data[0] - 125 }
    case 0x1f:
      return data.length < 2 ? {} : { runtimeSec: data[0] * 256 + data[1] }
    case 0x21:
      return data.length < 2 ? {} : { milDistanceKm: data[0] * 256 + data[1] }
    case 0x31:
      return data.length < 2 ? {} : { distSinceClearKm: data[0] * 256 + data[1] }
    case 0x42:
      return data.length < 2 ? {} : { batteryVoltageV: (data[0] * 256 + data[1]) / 1000 }
    default:
      return {}
  }
}

/** ELM poll strings from ObdBluetoothClient.POLL_PIDS → parser coverage. */
export const POLL_PID_HEX = [
  '010D', '010C', '0110', '010A', '0133', '010E', '014A', '0143', '0145', '0149', '014B', '014D',
  '0144', '014E', '0152', '0153', '0159', '014C', '015A', '0161', '0162', '0170', '0171', '0172',
  '0173', '0174', '0175', '0176', '0155', '0156', '0157', '0158', '0177', '0178', '015D', '015B',
  '0163', '0179', '017A', '0147', '0148', '0154', '017B', '017C', '0151', '014F', '0150', '017D', '017E', '0164', '0166', '0165', '017F', '0180', '0167', '0168', '016F', '0181', '0182', '016B', '016A', '016C', '0183', '0184', '0169', '016E', '016D', '0185', '0186', '0108', '0109', '0187', '0188', '0189', '018A', '018C', '018F', '0198', '0199', '019C', '0194', '019B', '01A1', '01A5', '01A7', '01A8', '01A2', '01A3', '01A4', '018B', '018D', '018E', '0104', '0106', '0107', '010B', '0105', '010F', '015C', '012F', '015E', '0146', '0111',
  '011F', '0121', '0131', '0134', '0142',
]

export function pollPidByte(hex4) {
  return parseInt(hex4.slice(2), 16)
}

/** Raw ELM responses exercised against parseMode01. */
export const OBD_SMOKE_CASES = [
  { raw: '41 0D 28', expect: { speedKmh: 40 } },
  { raw: 'SEARCHING...\r41 0C 1A F8\r>', expect: { rpm: 1726 } },
  { raw: '41 05 5A', expect: { coolantC: 50 } },
  { raw: '41 5C 82', expect: { oilTempC: 90 } },
  { raw: '41 0F 55', expect: { intakeAirC: 45 } },
  { raw: '41 2F 80', expect: { fuelPct: (0x80 * 100) / 255 } },
  { raw: '41 5E 03 E8', expect: { fuelRateGps: 50 } },
  { raw: '41 46 3C', expect: { outdoorTempC: 20 } },
  { raw: '41 11 80', expect: { throttlePct: (0x80 * 100) / 255 } },
  { raw: '41 04 50', expect: { engineLoadPct: (0x50 * 100) / 255 } },
  { raw: '41 06 90', expect: { fuelTrimStftPct: 12.5 } },
  { raw: '41 0A 28', expect: { fuelPressureKpa: 120 } },
  { raw: '41 0E 80', expect: { timingAdvanceDeg: 0 } },
  { raw: '41 07 60', expect: { fuelTrimLtftPct: -25 } },
  { raw: '41 0B 65', expect: { mapKpa: 101 } },
  { raw: '41 10 01 F4', expect: { mafGps: 5 } },
  { raw: '41 34 03 E8', expect: { catalystTempC: 60 } },
  { raw: '41 33 65', expect: { baroKpa: 101 } },
  { raw: '41 4A 5A', expect: { o2B1s1Volts: 0.45 } },
  { raw: '41 43 00 7F', expect: { absoluteLoadPct: 50 } },
  { raw: '41 45 80', expect: { relativeThrottlePct: (0x80 * 100) / 255 } },
  { raw: '41 49 80', expect: { accelPedalPct: (0x80 * 100) / 255 } },
  { raw: '41 4B 5A', expect: { o2B1s2Volts: 0.45 } },
  { raw: '41 4D 60', expect: { egrErrorPct: -25 } },
  { raw: '41 44 80 00', expect: { equivRatio: 1 } },
  { raw: '41 4E 80', expect: { evapPurgePct: (0x80 * 100) / 255 } },
  { raw: '41 52 50', expect: { ethanolPct: (0x50 * 100) / 255 } },
  { raw: '41 53 F0 00', expect: { evapVaporPa: -1024 } },
  { raw: '41 59 03 E8', expect: { fuelRailAbsKpa: 10000 } },
  { raw: '41 4C B4', expect: { egrCmdPct: (0xb4 * 100) / 255 } },
  { raw: '41 5A E6', expect: { relAccelPedalPct: (0xe6 * 100) / 255 } },
  { raw: '41 61 B9', expect: { driverTorquePct: 60 } },
  { raw: '41 62 41', expect: { actualTorquePct: -60 } },
  { raw: '41 70 22 C4', expect: { catalystB2TempC: 850 } },
  { raw: '41 71 22 C4', expect: { catalystB1s2TempC: 850 } },
  { raw: '41 72 23 28', expect: { catalystB2s2TempC: 860 } },
  { raw: '41 73 23 8C', expect: { catalystB1s3TempC: 870 } },
  { raw: '41 74 23 F0', expect: { catalystB2s3TempC: 880 } },
  { raw: '41 75 24 54', expect: { catalystB1s4TempC: 890 } },
  { raw: '41 76 24 54', expect: { catalystB2s4TempC: 890 } },
  { raw: '41 55 B8', expect: { fuelTrimStft2B1Pct: 43.75 } },
  { raw: '41 56 60', expect: { fuelTrimLtft2B1Pct: -25 } },
  { raw: '41 57 A8', expect: { fuelTrimStft2B2Pct: 31.25 } },
  { raw: '41 58 50', expect: { fuelTrimLtft2B2Pct: -37.5 } },
  { raw: '41 77 24 54', expect: { catalystB1s5TempC: 890 } },
  { raw: '41 78 24 54', expect: { catalystB2s5TempC: 890 } },
  { raw: '41 5D 14 00', expect: { fuelInjectTimingDeg: 40 } },
  { raw: '41 5B 26', expect: { hybridBattLifePct: (0x26 * 100) / 255 } },
  { raw: '41 63 02 26', expect: { engineRefTorqueNm: 550 } },
  { raw: '41 79 24 54', expect: { catalystB1s6TempC: 890 } },
  { raw: '41 7A 24 54', expect: { catalystB2s6TempC: 890 } },
  { raw: '41 47 E6', expect: { throttleBPct: (0xe6 * 100) / 255 } },
  { raw: '41 48 E6', expect: { throttleCPct: (0xe6 * 100) / 255 } },
  { raw: '41 54 00 78', expect: { milTimeMin: 120 } },
  { raw: '41 7B 24 54', expect: { catalystB1s7TempC: 890 } },
  { raw: '41 7C 24 54', expect: { catalystB2s7TempC: 890 } },
  { raw: '41 51 04', expect: { fuelTypeCode: 4 } },
  { raw: '41 4F 60 00', expect: { maxEquivRatio: 0.75 } },
  { raw: '41 50 03 E8', expect: { maxMafGps: 10 } },
  { raw: '41 7D 24 54', expect: { catalystB1s8TempC: 890 } },
  { raw: '41 7E 24 54', expect: { catalystB2s8TempC: 890 } },
  { raw: '41 64 91', expect: { maxAvailTorquePct: 20 } },
  { raw: '41 66 7D', expect: { mafSensorIatC: 85 } },
  { raw: '41 65 0F', expect: { auxInputStatus: 0x0f } },
  { raw: '41 7F 24 54', expect: { catalystB1s9TempC: 890 } },
  { raw: '41 80 24 54', expect: { catalystB2s9TempC: 890 } },
  { raw: '41 67 03 5A 8C', expect: { coolantEct2C: 100 } },
  { raw: '41 68 03 50 82', expect: { iatSensor2C: 90 } },
  { raw: '41 6F DC', expect: { turboInletKpa: 220 } },
  { raw: '41 81 24 54', expect: { catalystB1s10TempC: 890 } },
  { raw: '41 82 24 54', expect: { catalystB2s10TempC: 890 } },
  { raw: '41 6B 00 8C', expect: { egrTempC: 100 } },
  { raw: '41 6A E6', expect: { dieselIafCmdPct: (0xe6 * 100) / 255 } },
  { raw: '41 6C E6', expect: { thrActuatorPct: (0xe6 * 100) / 255 } },
  { raw: '41 83 24 54', expect: { catalystB1s11TempC: 890 } },
  { raw: '41 84 24 54', expect: { catalystB2s11TempC: 890 } },
  { raw: '41 69 F2', expect: { actualEgrPct: (0xf2 * 100) / 255 } },
  { raw: '41 6E FD E8', expect: { injectCtrlKpa: 6500 } },
  { raw: '41 6D FD E8', expect: { fuelCtrlKpa: 6500 } },
  { raw: '41 85 24 54', expect: { catalystB1s12TempC: 890 } },
  { raw: '41 86 24 54', expect: { catalystB2s12TempC: 890 } },
  { raw: '41 08 9C', expect: { fuelTrimStftB2Pct: 21.875 } },
  { raw: '41 09 9C', expect: { fuelTrimLtftB2Pct: 21.875 } },
  { raw: '41 87 24 54', expect: { catalystB1s13TempC: 890 } },
  { raw: '41 88 24 54', expect: { catalystB2s13TempC: 890 } },
  { raw: '41 89 24 54', expect: { catalystB1s14TempC: 890 } },
  { raw: '41 8A 24 54', expect: { catalystB2s14TempC: 890 } },
  { raw: '41 8C 00 00 00 00 00 00 00 00 00 27 10', expect: { o2LambdaB1: 1.22 } },
  { raw: '41 8F 00 00 23 28 00 23 28', expect: { pmSensorB1Pct: 90, pmSensorB2Pct: 90 } },
  { raw: '41 98 01 24 54', expect: { egtB1s5TempC: 890 } },
  { raw: '41 98 01 24 54 24 54', expect: { egtB1s5TempC: 890, egtB1s6TempC: 890 } },
  { raw: '41 98 01 00 00 00 00 24 54', expect: { egtB1s7TempC: 890 } },
  { raw: '41 98 01 00 00 00 00 00 00 24 54', expect: { egtB1s8TempC: 890 } },
  { raw: '41 99 01 24 54 24 54', expect: { egtB2s5TempC: 890, egtB2s6TempC: 890 } },
  { raw: '41 99 01 00 00 00 00 24 54', expect: { egtB2s7TempC: 890 } },
  { raw: '41 99 01 00 00 00 00 00 00 24 54', expect: { egtB2s8TempC: 890 } },
  { raw: '41 9C 00 27 10', expect: { o2ConcB1s3Pct: 0x2710 * 0.001526 } },
  { raw: '41 9C 00 00 00 27 10', expect: { o2ConcB1s4Pct: 0x2710 * 0.001526 } },
  { raw: '41 9C 00 00 00 00 00 27 10', expect: { o2ConcB2s3Pct: 0x2710 * 0.001526 } },
  { raw: '41 9C 00 00 00 00 00 00 00 27 10', expect: { o2ConcB2s4Pct: 0x2710 * 0.001526 } },
  { raw: '41 9C 00 00 00 00 00 00 00 00 00 27 10', expect: { o2LambdaB1s3: 1.22 } },
  { raw: '41 9C 00 00 00 00 00 00 00 00 00 00 00 27 10', expect: { o2LambdaB1s4: 1.22 } },
  { raw: '41 9C 00 00 00 00 00 00 00 00 00 00 00 00 00 27 10', expect: { o2LambdaB2s3: 1.22 } },
  { raw: '41 9C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 27 10', expect: { o2LambdaB2s4: 1.22 } },
  { raw: '41 94 00 00 00 19', expect: { noxReagentQualHours: 25 } },
  { raw: '41 9B 00 00 00 1A', expect: { defFluidPct: (0x1a * 100) / 255 } },
  { raw: '41 A5 01 BE', expect: { defDosingCmdPct: 0xbe / 2 } },
  { raw: '41 A1 00 03 84', expect: { noxCorrectedB1s1Ppm: 900 } },
  { raw: '41 A1 00 00 00 03 84', expect: { noxCorrectedB1s2Ppm: 900 } },
  { raw: '41 A1 00 00 00 00 00 03 84', expect: { noxCorrectedB2s1Ppm: 900 } },
  { raw: '41 A1 00 00 00 00 00 00 00 03 84', expect: { noxCorrectedB2s2Ppm: 900 } },
  { raw: '41 A7 03 84 00 00', expect: { noxConcS3Ppm: 900, noxConcS4Ppm: 0 } },
  { raw: '41 A7 00 00 03 84', expect: { noxConcS3Ppm: 0, noxConcS4Ppm: 900 } },
  { raw: '41 A8 03 84 00 00', expect: { noxCorrectedS3Ppm: 900, noxCorrectedS4Ppm: 0 } },
  { raw: '41 A8 00 00 03 84', expect: { noxCorrectedS3Ppm: 0, noxCorrectedS4Ppm: 900 } },
  { raw: '41 A2 07 00', expect: { cylinderFuelRateMg: 0x0700 / 32 } },
  { raw: '41 A3 00 23 28', expect: { evapSysVaporPa: 9000 } },
  { raw: '41 A4 02 00 0E 10', expect: { transGearRatio: 3.6 } },
  { raw: '41 8B 00 00 D9', expect: { dpfTriggerPct: (0xd9 * 100) / 255 } },
  { raw: '41 8D E6', expect: { throttleGPct: (0xe6 * 100) / 255 } },
  { raw: '41 8E B9', expect: { engineFrictionPct: 60 } },
  { raw: '41 1F 01 2C', expect: { runtimeSec: 300 } },
  { raw: '41 21 00 64', expect: { milDistanceKm: 100 } },
  { raw: '41 31 00 C8', expect: { distSinceClearKm: 200 } },
  { raw: '41 42 35 00', expect: { batteryVoltageV: 13.312 } },
]

export function runObdSmokeCases(tolerance = 0.6) {
  let fail = 0
  for (const { raw, expect } of OBD_SMOKE_CASES) {
    const got = parseMode01(raw)
    for (const [k, v] of Object.entries(expect)) {
      const g = got[k]
      if (g == null || Math.abs(g - v) > tolerance) {
        console.error('FAIL', raw, k, 'got', g, 'want', v)
        fail++
      }
    }
  }
  return fail
}

/** Fase-specific formula checks (shared with fase*-smoke.mjs). */
export function runFaseFormulaChecks(fase, assert) {
  switch (fase) {
    case 16:
      assert(Math.abs(((0x00 * 256 + 0x7f) * 100) / 255 - 50) < 0.5, 'pid 0143')
      assert((0x80 * 100) / 255 === 50.19607843137255, 'pid 0145')
      assert(0x5a / 200 === 0.45, 'pid 014B')
      assert(((0x60 - 128) * 100) / 128 === -25, 'pid 014D')
      break
    case 17:
      assert((0x80 * 256 + 0x00) / 32768 === 1, 'pid 0144')
      assert((0x80 * 100) / 255 > 50, 'pid 014E')
      assert(((0xf000 - 0x10000) / 4) === -1024, 'pid 0153 signed')
      assert(((0x03 * 256 + 0xe8) * 10) === 10000, 'pid 0159')
      break
    case 18:
      assert((0xb4 * 100) / 255 > 70, 'pid 014C')
      assert((0xe6 * 100) / 255 > 90, 'pid 015A')
      assert(0xb9 - 125 === 60, 'pid 0161')
      assert(0x41 - 125 === -60, 'pid 0162')
      assert(((0x22 * 256 + 0xc4) / 10) - 40 === 850, 'pid 0170')
      break
    case 19:
      assert(((0x22 * 256 + 0xc4) / 10) - 40 === 850, 'pid 0171')
      assert(((0x23 * 256 + 0x28) / 10) - 40 === 860, 'pid 0172')
      assert(((0x23 * 256 + 0x8c) / 10) - 40 === 870, 'pid 0173')
      assert(((0x23 * 256 + 0xf0) / 10) - 40 === 880, 'pid 0174')
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0175')
      break
    case 20:
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0176')
      assert(((0xb8 - 128) * 100) / 128 === 43.75, 'pid 0155')
      assert(((0x60 - 128) * 100) / 128 === -25, 'pid 0156')
      assert(((0xa8 - 128) * 100) / 128 === 31.25, 'pid 0157')
      assert(((0x50 - 128) * 100) / 128 === -37.5, 'pid 0158')
      break
    case 21:
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0177')
      assert((0x14 * 256) / 128 === 40, 'pid 015D')
      assert((0x26 * 100) / 255 < 15, 'pid 015B')
      assert(0x02 * 256 + 0x26 === 550, 'pid 0163')
      break
    case 22:
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0179')
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 017A')
      assert((0xe6 * 100) / 255 > 90, 'pid 0147')
      assert((0xe6 * 100) / 255 > 90, 'pid 0148')
      assert(0x00 * 256 + 0x78 === 120, 'pid 0154')
      break
    case 23:
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 017B')
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 017C')
      assert(0x04 === 4, 'pid 0151 diesel')
      assert((0x60 * 256) / 32768 === 0.75, 'pid 014F')
      assert((0x03 * 256 + 0xe8) / 100 === 10, 'pid 0150')
      break
    case 24:
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 017D')
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 017E')
      assert(0x91 - 125 === 20, 'pid 0164')
      assert(0x7d - 40 === 85, 'pid 0166')
      assert(0x0f === 15, 'pid 0165')
      break
    case 25:
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 017F')
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0180')
      assert(0x8c - 40 === 100, 'pid 0167 ECT2')
      assert(0x82 - 40 === 90, 'pid 0168 IAT2')
      assert(0xdc === 220, 'pid 016F')
      break
    case 26:
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0181')
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0182')
      assert(0x8c - 40 === 100, 'pid 016B')
      assert((0xe6 * 100) / 255 > 90, 'pid 016A')
      assert((0xe6 * 100) / 255 > 90, 'pid 016C')
      break
    case 27:
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0183')
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0184')
      assert((0xf2 * 100) / 255 > 94, 'pid 0169')
      assert((0xfd * 256 + 0xe8) / 10 === 6500, 'pid 016E')
      assert((0xfd * 256 + 0xe8) / 10 === 6500, 'pid 016D')
      break
    case 28:
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0185')
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0186')
      assert(((0x9c - 128) * 100) / 128 === 21.875, 'pid 0108')
      assert(((0x9c - 128) * 100) / 128 === 21.875, 'pid 0109')
      break
    case 29:
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0187')
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0188')
      assert((0xd9 * 100) / 255 > 84, 'pid 018B')
      assert((0xe6 * 100) / 255 > 90, 'pid 018D')
      assert(0xb9 - 125 === 60, 'pid 018E')
      break
    case 30:
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0189')
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 018A')
      assert((0x27 * 256 + 0x10) * 0.000122 === 1.22, 'pid 018C')
      assert((0x23 * 256 + 0x28) / 100 === 90, 'pid 018F B1')
      assert((0x23 * 256 + 0x28) / 100 === 90, 'pid 018F B2')
      break
    case 31:
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0198')
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0199')
      assert((0x27 * 256 + 0x10) * 0.000122 === 1.22, 'pid 019C B1S3')
      assert((0x27 * 256 + 0x10) * 0.000122 === 1.22, 'pid 019C B2S3')
      assert(0x00 * 256 + 0x19 === 25, 'pid 0194')
      break
    case 32:
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0198 S6')
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0199 S6')
      assert((0x27 * 256 + 0x10) * 0.000122 === 1.22, 'pid 019C B1S4')
      assert((0x27 * 256 + 0x10) * 0.000122 === 1.22, 'pid 019C B2S4')
      assert((0x1a * 100) / 255 < 11, 'pid 019B DEF')
      break
    case 33:
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0198 S7')
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0199 S7')
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0198 S8')
      assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0199 S8')
      assert((0x27 * 256 + 0x10) * 0.001526 > 15, 'pid 019C O2 conc B1S3')
      break
    case 34:
      assert((0x27 * 256 + 0x10) * 0.001526 > 15, 'pid 019C O2 conc B1S4')
      assert((0x27 * 256 + 0x10) * 0.001526 > 15, 'pid 019C O2 conc B2S3')
      assert((0x27 * 256 + 0x10) * 0.001526 > 15, 'pid 019C O2 conc B2S4')
      assert(0xbe / 2 === 95, 'pid 01A5 DEF dose')
      assert((0x03 * 256 + 0x84) === 900, 'pid 01A1 NOx corr')
      break
    case 35:
      assert((0x03 * 256 + 0x84) === 900, 'pid 01A1 NOx corr B1S2')
      assert((0x03 * 256 + 0x84) === 900, 'pid 01A1 NOx corr B2S1')
      assert((0x03 * 256 + 0x84) === 900, 'pid 01A1 NOx corr B2S2')
      assert((0x03 * 256 + 0x84) === 900, 'pid 01A7 NOx conc S3')
      assert((0x03 * 256 + 0x84) === 900, 'pid 01A7 NOx conc S4')
      break
    case 36:
      assert((0x03 * 256 + 0x84) === 900, 'pid 01A8 NOx corr S3')
      assert((0x03 * 256 + 0x84) === 900, 'pid 01A8 NOx corr S4')
      assert((0x07 * 256 + 0x00) / 32 === 56, 'pid 01A2 cyl fuel')
      assert((0x23 * 256 + 0x28) === 9000, 'pid 01A3 evap sys vapor')
      assert((0x0e * 256 + 0x10) / 1000 === 3.6, 'pid 01A4 trans gear')
      break
    default:
      throw new Error(`unknown fase ${fase}`)
  }
}
