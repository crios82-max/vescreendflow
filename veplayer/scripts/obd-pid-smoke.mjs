#!/usr/bin/env node
/** Mirrors ObdPidParser Mode 01 decode for CI/smoke without Android SDK. */
function extract(raw) {
  let cleaned = raw
    .toUpperCase()
    .replace(/SEARCHING\.\.\./g, '')
    .replace(/SEARCHING…/g, '')
    .replace(/STOPPED|NO DATA|UNABLE TO CONNECT|BUS INIT|OK|>/g, '')
    .replace(/[\r\n]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  if (!cleaned || cleaned.includes('ERROR') || cleaned === '?') return null
  const tokens = cleaned.split(' ').filter((t) => /^[0-9A-F]{2}$/.test(t))
  const ints = tokens.map((t) => parseInt(t, 16))
  const idx = ints.indexOf(0x41)
  if (idx < 0 || idx + 1 >= ints.length) return null
  return ints.slice(idx)
}

function parse(raw) {
  const bytes = extract(raw)
  if (!bytes || bytes.length < 3 || bytes[0] !== 0x41) return {}
  const pid = bytes[1]
  const data = bytes.slice(2)
  switch (pid) {
    case 0x0d:
      return { speedKmh: data[0] }
    case 0x0c:
      return { rpm: (data[0] * 256 + data[1]) / 4 }
    case 0x05:
      return { coolantC: data[0] - 40 }
    case 0x2f:
      return { fuelPct: (data[0] * 100) / 255 }
    case 0x46:
      return { outdoorTempC: data[0] - 40 }
    case 0x11:
      return { throttlePct: (data[0] * 100) / 255 }
    case 0x42:
      return { batteryVoltageV: (data[0] * 256 + data[1]) / 1000 }
    default:
      return {}
  }
}

const cases = [
  ['41 0D 28', { speedKmh: 40 }],
  ['SEARCHING...\r41 0C 1A F8\r>', { rpm: 1726 }],
  ['41 05 5A', { coolantC: 50 }],
  ['41 2F 80', { fuelPct: (0x80 * 100) / 255 }],
  ['41 42 35 00', { batteryVoltageV: (0x35 * 256 + 0x00) / 1000 }],
]

let fail = 0
for (const [raw, expect] of cases) {
  const got = parse(raw)
  for (const [k, v] of Object.entries(expect)) {
    const g = got[k]
    if (g == null || Math.abs(g - v) > 0.6) {
      console.error('FAIL', raw, k, 'got', g, 'want', v)
      fail++
    }
  }
}
if (fail) process.exit(1)
console.log('obd pid parser smoke OK', cases.length, 'cases')
