#!/usr/bin/env node
/**
 * OBD DTC / MIL smoke (VePlayer 0.35).
 * Parser unit tests + SenseFlow heartbeat alerts.
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function assert(c, m) {
  if (!c) throw new Error(m)
}

function extractMode(raw, ...modes) {
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
  const want = new Set(modes)
  const idx = ints.findIndex((x) => want.has(x))
  if (idx < 0) return null
  return ints.slice(idx)
}

function decodePair(hi, lo) {
  const type = ['P', 'C', 'B', 'U'][(hi >> 6) & 3]
  const d1 = (hi >> 4) & 3
  const d2 = (hi & 0xf).toString(16).toUpperCase()
  const d3 = ((lo >> 4) & 0xf).toString(16).toUpperCase()
  const d4 = (lo & 0xf).toString(16).toUpperCase()
  return `${type}${d1}${d2}${d3}${d4}`
}

function parseMonitorStatus(raw) {
  const bytes = extractMode(raw, 0x41)
  if (!bytes || bytes.length < 3 || bytes[0] !== 0x41 || bytes[1] !== 0x01) return null
  const a = bytes[2]
  return { mil: (a & 0x80) !== 0, count: a & 0x7f }
}

function parseDtcResponse(raw, expectedMode) {
  const echo = expectedMode === 0x03 ? 0x43 : expectedMode === 0x07 ? 0x47 : 0x4a
  const bytes = extractMode(raw, echo)
  if (!bytes || bytes[0] !== echo) return []
  const status = echo === 0x43 ? 'stored' : echo === 0x47 ? 'pending' : 'permanent'
  const out = []
  let i = 1
  const rest = bytes.length - 1
  if (rest >= 1) {
    const maybeCount = bytes[1]
    if (maybeCount >= 1 && maybeCount <= 0x10 && rest === 1 + maybeCount * 2) i = 2
  }
  while (i + 1 < bytes.length) {
    const hi = bytes[i]
    const lo = bytes[i + 1]
    i += 2
    if (hi === 0 && lo === 0) continue
    out.push({ code: decodePair(hi, lo), status })
  }
  return out
}

async function j(path, init = {}, token) {
  const headers = { 'content-type': 'application/json', ...(init.headers || {}) }
  if (token) headers['x-fleet-token'] = token
  const r = await fetch(BASE + path, { ...init, headers })
  const text = await r.text()
  let body
  try {
    body = JSON.parse(text)
  } catch {
    body = text
  }
  return { ok: r.ok, status: r.status, body }
}

async function main() {
  console.log('obd-dtc-smoke →', BASE)

  // Unit: P0133 from Mode 03
  const p0133 = parseDtcResponse('43 01 33 00 00', 0x03)
  assert(p0133.some((c) => c.code === 'P0133'), `P0133 got ${JSON.stringify(p0133)}`)

  // Unit: MIL on, 1 code — 41 01 81 …
  const mil = parseMonitorStatus('41 01 81 00 00 00')
  assert(mil && mil.mil === true && mil.count === 1, `mil ${JSON.stringify(mil)}`)

  // Unit: pending C1234 → hi=0x51 (01_010001? C=01, d1=1 → bits 7-6=01, 5-4=01 → 0x50|0x01 = 0x51), lo=0x34
  // C1234: type C=1, d1=1, d2=2, d3=3, d4=4 → hi = (1<<6)|(1<<4)|2 = 0x40|0x10|0x02 = 0x52, lo=0x34
  const pending = parseDtcResponse('47 52 34', 0x07)
  assert(pending.some((c) => c.code === 'C1234' && c.status === 'pending'), `C1234 ${JSON.stringify(pending)}`)

  const deviceId = `dtc-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'DTC smoke',
      app_version: '0.35.0',
      version_code: 37,
    }),
  })
  assert(reg.ok || reg.status === 201, `register ${reg.status}`)

  const hb = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.35.0',
      version_code: 37,
      lat: 10.5,
      lng: -66.9,
      vehicle_signals: {
        mil: true,
        dtc_count: 2,
        dtcs: [
          { code: 'P0420', status: 'stored' },
          { code: 'P0301', status: 'pending' },
        ],
        speed_mps: 10,
        ignition: 'on',
        source: 'obd_sim',
      },
    }),
  })
  assert(hb.ok, `hb ${hb.status}`)
  const raised = hb.body.alerts_raised || []
  assert(raised.includes('mil_on'), `mil_on in ${JSON.stringify(raised)}`)
  assert(
    raised.some((k) => k === 'dtc:P0420' || k === 'dtc:P0301'),
    `dtc in ${JSON.stringify(raised)}`,
  )

  const cmd = await j(
    '/api/fleet/command',
    {
      method: 'POST',
      body: JSON.stringify({
        device_id: deviceId,
        command: 'seed_dtc',
        payload: { mil: true, codes: ['P0420'] },
      }),
    },
    'fleet-dispatcher-demo',
  )
  assert(cmd.ok, `seed_dtc ${cmd.status} ${JSON.stringify(cmd.body)}`)

  const clr = await j(
    '/api/fleet/command',
    {
      method: 'POST',
      body: JSON.stringify({ device_id: deviceId, command: 'clear_dtc', payload: {} }),
    },
    'fleet-dispatcher-demo',
  )
  assert(clr.ok, `clear_dtc ${clr.status}`)

  const read = await j(
    '/api/fleet/command',
    {
      method: 'POST',
      body: JSON.stringify({ device_id: deviceId, command: 'read_dtc', payload: {} }),
    },
    'fleet-dispatcher-demo',
  )
  assert(read.ok, `read_dtc ${read.status}`)

  console.log('OK obd-dtc-smoke · raised', raised.filter((k) => k.includes('mil') || k.startsWith('dtc')))
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
