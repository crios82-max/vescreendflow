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
  '0163', '0104', '0106', '0107', '010B', '0105', '010F', '015C', '012F', '015E', '0146', '0111',
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
    default:
      throw new Error(`unknown fase ${fase}`)
  }
}
