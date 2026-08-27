#!/usr/bin/env node
/** Smoke: SLCAN parse + VePlayer demo DBC decode (mirrors Kotlin). */

function parseSlcan(line) {
  if (!line) return null
  try {
    if (line[0] === 't') {
      const id = parseInt(line.slice(1, 4), 16)
      const len = parseInt(line.slice(4, 5), 16)
      const hex = line.slice(5)
      const data = []
      for (let i = 0; i < len; i++) data.push(parseInt(hex.slice(i * 2, i * 2 + 2), 16))
      return { id, data, extended: false }
    }
    if (line[0] === 'T') {
      const id = parseInt(line.slice(1, 9), 16)
      const len = parseInt(line.slice(9, 10), 16)
      const hex = line.slice(10)
      const data = []
      for (let i = 0; i < len; i++) data.push(parseInt(hex.slice(i * 2, i * 2 + 2), 16))
      return { id, data, extended: true }
    }
  } catch {
    return null
  }
  return null
}

function decode(frame, base = {}) {
  const d = frame.data
  const id = frame.id & 0x7ff
  switch (id) {
    case 0x100:
      return { ...base, speedKmh: d[0] }
    case 0x101: {
      const g = ['P', 'R', 'N', 'D', 'L'][d[0]] || 'UNKNOWN'
      return { ...base, gear: g }
    }
    case 0x102: {
      const t = ['OFF', 'LEFT', 'RIGHT', 'HAZARD'][d[0]] || 'OFF'
      return { ...base, turn: t }
    }
    case 0x104:
      return { ...base, soc: d[0], fuel: d[1] }
    case 0x106:
      return { ...base, abs: !!(d[0] & 1), parking: !!(d[0] & 2) }
    case 0x107:
      return { ...base, tpms: d.slice(0, 4) }
    case 0x108:
      return { ...base, cabin: d[0], target: d[1], fan: d[2], ac: !!(d[3] & 1) }
    default:
      return base
  }
}

let fail = 0
const cases = [
  ['t100128', { id: 0x100, data: [0x28] }],
  ['t101101', { id: 0x101, data: [0x01] }],
  ['t102201', { id: 0x102, data: [0x01] }],
  ['T00000104400010203', { id: 0x104, data: [0, 1, 2, 3], extended: true }],
]

for (const [line, expect] of cases) {
  const got = parseSlcan(line)
  if (!got || got.id !== expect.id || got.data[0] !== expect.data[0]) {
    console.error('SLCAN FAIL', line, got, expect)
    fail++
  }
}

let snap = {}
snap = decode(parseSlcan('t100128'), snap)
snap = decode(parseSlcan('t101103'), snap)
snap = decode(parseSlcan('t102203'), snap)
snap = decode(parseSlcan('t10424700'), snap)
snap = decode(parseSlcan('t106101'), snap)
snap = decode(parseSlcan('t107420202121'), snap)
snap = decode(parseSlcan('t108418160201'), snap)

const checks = [
  snap.speedKmh === 0x28,
  snap.gear === 'D',
  snap.turn === 'HAZARD',
  snap.soc === 0x47,
  snap.abs === true,
  snap.tpms[0] === 0x20,
  snap.cabin === 0x18 && snap.ac === true,
]
if (checks.some((c) => !c)) {
  console.error('DECODE FAIL', snap, checks)
  fail++
}

if (fail) process.exit(1)
console.log('can decoder/slcan smoke OK')
